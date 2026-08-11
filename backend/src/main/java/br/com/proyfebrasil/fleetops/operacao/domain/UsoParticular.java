package br.com.proyfebrasil.fleetops.operacao.domain;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
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
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;
import org.hibernate.envers.Audited;

/**
 * Autorização de uso particular do veículo (RN-10).
 *
 * <p>Três números vêm da regra e não são configuráveis: o teto de <strong>1.000 km</strong>
 * por período autorizado, o horário-limite de condução às <strong>20:00</strong>, e a
 * atribuição de infrações e custos ao condutor. Deixá-los em configuração transformaria
 * uma regra da empresa em preferência de instalação.
 *
 * <p>O aceite das regras é condição de validade, não formalidade. A RN-10 responsabiliza
 * o condutor por multas e danos no período — responsabilização que só se sustenta se ele
 * tiver declarado conhecer as regras. Uma autorização sem aceite não vale, e
 * {@link #estaValida()} diz isso em vez de deixar o chamador adivinhar.
 */
@Entity
@Table(name = "uso_particular")
@Audited
public class UsoParticular extends EntidadeAuditavel {

    /** Teto de quilometragem por período autorizado (RN-10). */
    public static final int LIMITE_DE_KM = 1000;

    /** Depois deste horário a condução em uso particular é proibida (RN-10). */
    public static final LocalTime HORARIO_LIMITE = LocalTime.of(20, 0);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoDeLocacao contrato;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condutor_id", nullable = false)
    private Condutor condutor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoDeUsoParticular tipo;

    @Column(name = "inicio", nullable = false)
    private LocalDate inicio;

    @Column(name = "fim", nullable = false)
    private LocalDate fim;

    @Column(name = "km_autorizado", nullable = false)
    private int kmAutorizado = LIMITE_DE_KM;

    @Column(name = "km_percorrido")
    private Integer kmPercorrido;

    @Column(name = "aceite_em")
    private Instant aceiteEm;

    @Column(name = "observacoes")
    private String observacoes;

    /** Construtor exigido pelo JPA. */
    protected UsoParticular() {
    }

    public UsoParticular(
            ContratoDeLocacao contrato,
            Condutor condutor,
            TipoDeUsoParticular tipo,
            LocalDate inicio,
            LocalDate fim) {
        this.contrato = Objects.requireNonNull(contrato, "contrato é obrigatório");
        this.condutor = Objects.requireNonNull(condutor, "condutor é obrigatório");
        this.tipo = Objects.requireNonNull(tipo, "tipo é obrigatório");
        this.inicio = Objects.requireNonNull(inicio, "início é obrigatório");
        this.fim = Objects.requireNonNull(fim, "fim é obrigatório");
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("O fim do período não pode ser anterior ao início.");
        }
    }

    /**
     * Define quantos quilômetros o período autoriza.
     *
     * @throws NegocioException se ultrapassar o teto da RN-10
     */
    public void autorizarKm(int km) {
        if (km <= 0) {
            throw new IllegalArgumentException("A quilometragem autorizada deve ser maior que zero.");
        }
        if (km > LIMITE_DE_KM) {
            throw new NegocioException(
                    ErroOperacao.USO_PARTICULAR_ACIMA_DO_LIMITE,
                    "A RN-10 limita o uso particular a %d km por período; foram solicitados %d km."
                            .formatted(LIMITE_DE_KM, km),
                    Map.of("limite", LIMITE_DE_KM, "solicitado", km));
        }
        this.kmAutorizado = km;
    }

    /** Registra o aceite das regras pelo condutor — condição de validade da autorização. */
    public void registrarAceite(Instant quando) {
        this.aceiteEm = Objects.requireNonNull(quando, "momento do aceite é obrigatório");
    }

    /** Registra quanto foi de fato rodado, para conferência ao fim do período. */
    public void registrarKmPercorrido(Integer km) {
        if (km != null && km < 0) {
            throw new IllegalArgumentException("A quilometragem percorrida não pode ser negativa.");
        }
        this.kmPercorrido = km;
    }

    public void alterarObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    /** Uma autorização sem aceite não responsabiliza ninguém, e portanto não vale (RN-10). */
    public boolean estaValida() {
        return aceiteEm != null;
    }

    /** Indica se o rodado ultrapassou o autorizado — apurável só depois do período. */
    public boolean excedeuOAutorizado() {
        return kmPercorrido != null && kmPercorrido > kmAutorizado;
    }

    /** Quanto passou do autorizado; zero quando dentro do limite ou ainda sem apuração. */
    public int kmExcedido() {
        return excedeuOAutorizado() ? kmPercorrido - kmAutorizado : 0;
    }

    /**
     * Indica se a condução naquele horário viola a RN-10.
     *
     * <p>Exposto como consulta, e não como validação de escrita, porque o sistema registra
     * a autorização — não acompanha o carro. Quem usa isto é a apuração de uma ocorrência
     * já acontecida: uma multa às 21:30 dentro de um período de uso particular.
     */
    public static boolean horarioProibido(LocalTime horario) {
        return horario != null && horario.isAfter(HORARIO_LIMITE);
    }

    /** Indica se a data cai dentro do período autorizado. */
    public boolean cobre(LocalDate data) {
        return data != null && !data.isBefore(inicio) && !data.isAfter(fim);
    }

    public Long getId() {
        return id;
    }

    public ContratoDeLocacao getContrato() {
        return contrato;
    }

    public Condutor getCondutor() {
        return condutor;
    }

    public TipoDeUsoParticular getTipo() {
        return tipo;
    }

    public LocalDate getInicio() {
        return inicio;
    }

    public LocalDate getFim() {
        return fim;
    }

    public int getKmAutorizado() {
        return kmAutorizado;
    }

    public Integer getKmPercorrido() {
        return kmPercorrido;
    }

    public Instant getAceiteEm() {
        return aceiteEm;
    }

    public String getObservacoes() {
        return observacoes;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof UsoParticular uso)) {
            return false;
        }
        return id != null && id.equals(uso.id);
    }

    @Override
    public int hashCode() {
        return UsoParticular.class.hashCode();
    }
}
