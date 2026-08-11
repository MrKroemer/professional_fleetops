package br.com.proyfebrasil.fleetops.contratos.domain;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.shared.arquivos.Anexo;
import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

/**
 * Retirada ou devolução de um veículo (Seção 3.2, RN-12).
 *
 * <p>Reúne o que a especificação exige de cada um desses momentos: checklist da locadora,
 * book fotográfico, CRLV anexado e a confirmação de que as regras de uso foram enviadas e
 * aceitas pelo condutor.
 *
 * <p>O veículo é gravado no evento, e não lido do contrato. A retirada pode ser de um
 * substituto, e a devolução quase sempre é de um veículo que já não é o atual — amarrar
 * ao ponteiro do contrato faria o histórico mentir depois da primeira substituição.
 *
 * <p>A regra central está em {@link #concluir(Instant)}: a RN-12 proíbe conclusão parcial
 * silenciosa, então a checagem acontece aqui, no domínio, e não na tela. O que falta é
 * devolvido <em>item a item</em>, e não como um "book incompleto" genérico: quem está no
 * pátio precisa saber que falta a foto do porta-malas, não que algo falta.
 */
@Entity
@Table(name = "evento_de_contrato")
@Audited
public class EventoDeContrato extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoDeLocacao contrato;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condutor_id")
    private Condutor condutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crlv_anexo_id")
    private Anexo crlv;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoDeEvento tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", length = 20, nullable = false)
    private SituacaoDoEvento situacao = SituacaoDoEvento.EM_PREENCHIMENTO;

    @Column(name = "data_do_evento", nullable = false)
    private LocalDate dataDoEvento;

    @Column(name = "km")
    private Integer km;

    @Column(name = "local_do_evento", length = 180)
    private String localDoEvento;

    @Column(name = "checklist_locadora")
    private String checklistDaLocadora;

    @Column(name = "regras_aceitas_em")
    private Instant regrasAceitasEm;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    @Column(name = "observacoes")
    private String observacoes;

    /**
     * Fotos do book.
     *
     * <p>Fora da trilha do Envers: cada foto é imutável depois de enviada — trocar uma
     * significa apagar e enviar outra —, e o {@code sha256} do anexo já sustenta a prova
     * de integridade que uma versão do Envers tentaria dar.
     */
    @NotAudited
    @BatchSize(size = 20)
    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FotoDoBook> fotos = new ArrayList<>();

    /** Construtor exigido pelo JPA. */
    protected EventoDeContrato() {
    }

    public EventoDeContrato(
            ContratoDeLocacao contrato, TipoDeEvento tipo, Veiculo veiculo, LocalDate dataDoEvento) {
        this.contrato = Objects.requireNonNull(contrato, "contrato é obrigatório");
        this.tipo = Objects.requireNonNull(tipo, "tipo do evento é obrigatório");
        this.veiculo = Objects.requireNonNull(veiculo, "veículo é obrigatório");
        this.dataDoEvento = Objects.requireNonNull(dataDoEvento, "data do evento é obrigatória");
        this.situacao = SituacaoDoEvento.EM_PREENCHIMENTO;
    }

    public void preencher(
            Condutor condutor, Integer km, String localDoEvento, String checklistDaLocadora, String observacoes) {
        exigirEmPreenchimento();
        this.condutor = condutor;
        if (km != null && km < 0) {
            throw new IllegalArgumentException("A quilometragem não pode ser negativa.");
        }
        this.km = km;
        this.localDoEvento = localDoEvento;
        this.checklistDaLocadora = checklistDaLocadora;
        this.observacoes = observacoes;
    }

    /** Registra o aceite das regras de uso pelo condutor (Seção 3.2). */
    public void registrarAceiteDasRegras(Instant quando) {
        exigirEmPreenchimento();
        this.regrasAceitasEm = Objects.requireNonNull(quando, "momento do aceite é obrigatório");
    }

    public void anexarCrlv(Anexo crlv) {
        exigirEmPreenchimento();
        this.crlv = Objects.requireNonNull(crlv, "CRLV é obrigatório");
    }

    /**
     * Acrescenta uma foto ao book.
     *
     * <p>Reenviar um ângulo obrigatório substitui a foto anterior em vez de acumular: o
     * caso real é a foto que saiu tremida ou contra a luz, e guardar as duas deixaria o
     * book ambíguo sobre qual delas vale. AVARIAS é a exceção — várias avarias, vários
     * registros.
     */
    public FotoDoBook adicionarFoto(ItemDoBook item, Anexo anexo, String observacao) {
        exigirEmPreenchimento();
        Objects.requireNonNull(item, "item do book é obrigatório");
        if (!item.aceitaVarias()) {
            fotos.removeIf(foto -> foto.getItem() == item);
        }
        FotoDoBook foto = new FotoDoBook(this, item, anexo, observacao);
        fotos.add(foto);
        return foto;
    }

    /** Remove a foto do ângulo informado; devolve se havia algo a remover. */
    public boolean removerFoto(Long fotoId) {
        exigirEmPreenchimento();
        return fotos.removeIf(foto -> Objects.equals(foto.getId(), fotoId));
    }

    /**
     * Ângulos obrigatórios ainda sem foto (RN-12).
     *
     * <p>Devolvido também quando o evento já está completo — uma lista vazia é a resposta
     * de que nada falta, e obriga o chamador a olhar para o conteúdo em vez de para um
     * booleano que esconde qual é o problema.
     */
    public List<ItemDoBook> itensFaltantesDoBook() {
        Set<ItemDoBook> enviados = fotos.isEmpty()
                ? EnumSet.noneOf(ItemDoBook.class)
                : EnumSet.copyOf(fotos.stream().map(FotoDoBook::getItem).toList());
        return ItemDoBook.obrigatorios().stream().filter(item -> !enviados.contains(item)).toList();
    }

    /** Indica se o evento reúne tudo o que a RN-12 exige para ser concluído. */
    public boolean estaCompleto() {
        return itensFaltantesDoBook().isEmpty() && crlv != null;
    }

    /**
     * Conclui o evento (RN-12).
     *
     * @throws NegocioException se faltar foto obrigatória ou o CRLV
     */
    public void concluir(Instant quando) {
        if (situacao == SituacaoDoEvento.CONCLUIDO) {
            throw new NegocioException(
                    ErroContrato.EVENTO_JA_CONCLUIDO,
                    "Este evento de %s já foi concluído em %s."
                            .formatted(tipo.getDescricao().toLowerCase(java.util.Locale.ROOT), dataDoEvento));
        }

        List<ItemDoBook> faltantes = itensFaltantesDoBook();
        if (!faltantes.isEmpty()) {
            throw new NegocioException(
                    ErroContrato.BOOK_INCOMPLETO,
                    "Faltam fotos obrigatórias no book: %s."
                            .formatted(String.join(", ", faltantes.stream().map(ItemDoBook::getDescricao).toList())),
                    Map.of("itensFaltantes", faltantes.stream().map(Enum::name).toList()));
        }
        if (crlv == null) {
            throw new NegocioException(
                    ErroContrato.CRLV_AUSENTE,
                    "O CRLV do veículo precisa ser anexado antes de concluir a %s."
                            .formatted(tipo.getDescricao().toLowerCase(java.util.Locale.ROOT)));
        }

        this.situacao = SituacaoDoEvento.CONCLUIDO;
        this.concluidoEm = Objects.requireNonNull(quando, "momento da conclusão é obrigatório");
    }

    /** Reabre um evento concluído para correção, mantendo o que já foi enviado. */
    public void reabrir() {
        this.situacao = SituacaoDoEvento.EM_PREENCHIMENTO;
        this.concluidoEm = null;
    }

    public boolean estaConcluido() {
        return situacao == SituacaoDoEvento.CONCLUIDO;
    }

    private void exigirEmPreenchimento() {
        if (situacao == SituacaoDoEvento.CONCLUIDO) {
            throw new NegocioException(
                    ErroContrato.EVENTO_JA_CONCLUIDO,
                    "Este evento já foi concluído. Reabra-o para alterar o conteúdo.");
        }
    }

    /** Fotos na ordem em que a tela as pede. */
    public List<FotoDoBook> getFotos() {
        return fotos.stream().sorted(Comparator.comparing(foto -> foto.getItem().ordinal())).toList();
    }

    public Long getId() {
        return id;
    }

    public ContratoDeLocacao getContrato() {
        return contrato;
    }

    public Veiculo getVeiculo() {
        return veiculo;
    }

    public Optional<Condutor> getCondutor() {
        return Optional.ofNullable(condutor);
    }

    public Optional<Anexo> getCrlv() {
        return Optional.ofNullable(crlv);
    }

    public TipoDeEvento getTipo() {
        return tipo;
    }

    public SituacaoDoEvento getSituacao() {
        return situacao;
    }

    public LocalDate getDataDoEvento() {
        return dataDoEvento;
    }

    public Integer getKm() {
        return km;
    }

    public String getLocalDoEvento() {
        return localDoEvento;
    }

    public String getChecklistDaLocadora() {
        return checklistDaLocadora;
    }

    public Instant getRegrasAceitasEm() {
        return regrasAceitasEm;
    }

    public Instant getConcluidoEm() {
        return concluidoEm;
    }

    public String getObservacoes() {
        return observacoes;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof EventoDeContrato evento)) {
            return false;
        }
        return id != null && id.equals(evento.id);
    }

    @Override
    public int hashCode() {
        return EventoDeContrato.class.hashCode();
    }
}
