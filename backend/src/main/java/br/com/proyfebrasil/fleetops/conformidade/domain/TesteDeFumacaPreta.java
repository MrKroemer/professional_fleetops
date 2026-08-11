package br.com.proyfebrasil.fleetops.conformidade.domain;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.cadastros.domain.Obra;
import br.com.proyfebrasil.fleetops.cadastros.domain.Veiculo;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.annotations.Generated;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

/**
 * Teste de fumaça preta — FOR.MA.01 (Seção 3.4, RN-09).
 *
 * <p>Obrigatório na retirada de veículo a diesel. O resultado é o padrão da escala de
 * Ringelmann que o avaliador comparou com a fumaça do escapamento.
 *
 * <p>O critério de aprovação depende da altitude: reprova acima do Padrão 2 até 500 m,
 * acima do Padrão 3 acima disso — o ar rarefeito reduz a eficiência da combustão, e a
 * norma acomoda isso. A regra está codificada na coluna gerada {@code conforme}, no
 * banco, e não em campo digitado: a RN-21 manda todo valor calculado ser derivado, e
 * assim nenhuma carga em lote consegue gravar "conforme" num teste reprovado.
 *
 * <p>Como a coluna é gerada pelo banco, o atributo é somente leitura no Java e precisa ser
 * relido após a gravação — daí {@link Generated}, que faz o Hibernate buscar o valor de
 * volta em vez de deixar o objeto em memória divergindo da linha.
 */
@Entity
@Table(name = "teste_fumaca_preta")
@Audited
public class TesteDeFumacaPreta extends EntidadeAuditavel {

    /** Acima desta altitude o critério afrouxa um padrão (Seção 3.4). */
    public static final int ALTITUDE_DE_CORTE_METROS = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id")
    private ContratoDeLocacao contrato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condutor_id")
    private Condutor condutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obra_id")
    private Obra obra;

    @Column(name = "data_do_teste", nullable = false)
    private LocalDate dataDoTeste;

    @Convert(converter = ConversorDePadraoRingelmann.class)
    @Column(name = "padrao_ringelmann", nullable = false)
    private PadraoRingelmann padrao;

    @Column(name = "altitude_metros", nullable = false)
    private int altitudeEmMetros;

    @Column(name = "observacoes")
    private String observacoes;

    /**
     * Resultado, calculado pelo banco.
     *
     * <p>Fora da trilha do Envers: é derivado do padrão e da altitude, que já são
     * versionados. Versioná-lo criaria uma segunda cópia da mesma informação, capaz de
     * divergir se o critério da Seção 3.4 mudasse — e a revisão antiga passaria a
     * afirmar um resultado que a regra da época não produzia.
     */
    @NotAudited
    @Generated
    @Column(name = "conforme", insertable = false, updatable = false)
    private Boolean conforme;

    /** Construtor exigido pelo JPA. */
    protected TesteDeFumacaPreta() {
    }

    public TesteDeFumacaPreta(
            Veiculo veiculo, LocalDate dataDoTeste, PadraoRingelmann padrao, int altitudeEmMetros) {
        this.veiculo = Objects.requireNonNull(veiculo, "veículo é obrigatório");
        this.dataDoTeste = Objects.requireNonNull(dataDoTeste, "data do teste é obrigatória");
        this.padrao = Objects.requireNonNull(padrao, "padrão de Ringelmann é obrigatório");
        if (altitudeEmMetros < 0) {
            throw new IllegalArgumentException("A altitude não pode ser negativa.");
        }
        this.altitudeEmMetros = altitudeEmMetros;
    }

    public void vincular(ContratoDeLocacao contrato, Condutor condutor, Obra obra) {
        this.contrato = contrato;
        this.condutor = condutor;
        this.obra = obra;
    }

    public void alterarObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    /**
     * Aplica o critério da Seção 3.4.
     *
     * <p>Repete em Java a regra que a coluna gerada aplica no banco. A duplicação é
     * deliberada e vale a pena: o domínio precisa responder sobre um teste que ainda não
     * foi gravado — a tela mostra aprovado ou reprovado enquanto o avaliador escolhe o
     * padrão —, e o banco precisa da sua própria cópia porque é a única barreira que uma
     * carga em lote não contorna. O teste de unidade compara as duas.
     */
    public static boolean aprovado(PadraoRingelmann padrao, int altitudeEmMetros) {
        int limite = altitudeEmMetros > ALTITUDE_DE_CORTE_METROS ? 3 : 2;
        return padrao.getCodigo() <= limite;
    }

    /**
     * Resultado do teste.
     *
     * <p>Antes da gravação a coluna gerada ainda não existe; nesse instante o valor sai do
     * cálculo em Java, que é a mesma regra.
     */
    public boolean isConforme() {
        return conforme != null ? conforme : aprovado(padrao, altitudeEmMetros);
    }

    /** Explicação do resultado, para exibição — o número sozinho não diz por que reprovou. */
    public String justificativa() {
        int limite = altitudeEmMetros > ALTITUDE_DE_CORTE_METROS ? 3 : 2;
        String faixa = altitudeEmMetros > ALTITUDE_DE_CORTE_METROS
                ? "acima de %d m de altitude".formatted(ALTITUDE_DE_CORTE_METROS)
                : "até %d m de altitude".formatted(ALTITUDE_DE_CORTE_METROS);
        return isConforme()
                ? "%s está dentro do limite (até o Padrão %d, %s).".formatted(padrao.getDescricao(), limite, faixa)
                : "%s ultrapassa o limite de Padrão %d %s.".formatted(padrao.getDescricao(), limite, faixa);
    }

    public Long getId() {
        return id;
    }

    public Veiculo getVeiculo() {
        return veiculo;
    }

    public Optional<ContratoDeLocacao> getContrato() {
        return Optional.ofNullable(contrato);
    }

    public Optional<Condutor> getCondutor() {
        return Optional.ofNullable(condutor);
    }

    public Optional<Obra> getObra() {
        return Optional.ofNullable(obra);
    }

    public LocalDate getDataDoTeste() {
        return dataDoTeste;
    }

    public PadraoRingelmann getPadrao() {
        return padrao;
    }

    public int getAltitudeEmMetros() {
        return altitudeEmMetros;
    }

    public String getObservacoes() {
        return observacoes;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof TesteDeFumacaPreta teste)) {
            return false;
        }
        return id != null && id.equals(teste.id);
    }

    @Override
    public int hashCode() {
        return TesteDeFumacaPreta.class.hashCode();
    }
}
