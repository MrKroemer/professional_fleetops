package br.com.proyfebrasil.fleetops.operacao.domain;

import br.com.proyfebrasil.fleetops.cadastros.domain.Condutor;
import br.com.proyfebrasil.fleetops.contratos.domain.ContratoDeLocacao;
import br.com.proyfebrasil.fleetops.shared.auditing.EntidadeAuditavel;
import jakarta.persistence.Column;
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
import org.hibernate.envers.Audited;

/**
 * Registro diário de quilometragem — digitalização do FOR.FRO.02 (RN-03).
 *
 * <p>A regra tem duas metades, e elas moram em lugares diferentes de propósito.
 *
 * <p>A primeira — {@code km_final ≥ km_inicial} — é uma invariante do próprio registro e
 * vive aqui, além de estar no banco como {@code CHECK}. A segunda — o KM inicial não pode
 * ser menor que o KM final do registro anterior — depende de <em>outro</em> registro, e
 * por isso vive no serviço, que é quem pode consultá-lo. Colocá-la na entidade obrigaria
 * a entidade a conhecer o repositório.
 *
 * <p>Um hodômetro que anda para trás não é um erro de digitação isolado: significa que
 * algum lançamento anterior está errado, ou que o veículo foi trocado sem registro. Daí a
 * mensagem citar o registro conflitante em vez de dizer apenas "valor inválido".
 */
@Entity
@Table(name = "registro_km")
@Audited
public class RegistroDeKm extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoDeLocacao contrato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condutor_id")
    private Condutor condutor;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "km_inicial", nullable = false)
    private int kmInicial;

    @Column(name = "km_final", nullable = false)
    private int kmFinal;

    @Column(name = "origem", length = 180)
    private String origem;

    @Column(name = "destino", length = 180)
    private String destino;

    @Column(name = "observacao")
    private String observacao;

    /** Construtor exigido pelo JPA. */
    protected RegistroDeKm() {
    }

    public RegistroDeKm(ContratoDeLocacao contrato, LocalDate data, int kmInicial, int kmFinal) {
        this.contrato = Objects.requireNonNull(contrato, "contrato é obrigatório");
        this.data = Objects.requireNonNull(data, "data é obrigatória");
        alterarQuilometragem(kmInicial, kmFinal);
    }

    /**
     * Ajusta a quilometragem do registro (primeira metade da RN-03).
     *
     * @throws IllegalArgumentException se o final for menor que o inicial ou algum for negativo
     */
    public final void alterarQuilometragem(int kmInicial, int kmFinal) {
        if (kmInicial < 0 || kmFinal < 0) {
            throw new IllegalArgumentException("A quilometragem não pode ser negativa.");
        }
        if (kmFinal < kmInicial) {
            throw new IllegalArgumentException(
                    "O KM final (%d) não pode ser menor que o inicial (%d).".formatted(kmFinal, kmInicial));
        }
        this.kmInicial = kmInicial;
        this.kmFinal = kmFinal;
    }

    public void alterarTrajeto(String origem, String destino, String observacao) {
        this.origem = origem;
        this.destino = destino;
        this.observacao = observacao;
    }

    public void definirCondutor(Condutor condutor) {
        this.condutor = condutor;
    }

    public void alterarData(LocalDate data) {
        this.data = Objects.requireNonNull(data, "data é obrigatória");
    }

    /** Distância rodada no dia. Derivada, nunca digitada (RN-21). */
    public int kmPercorrido() {
        return kmFinal - kmInicial;
    }

    public Long getId() {
        return id;
    }

    public ContratoDeLocacao getContrato() {
        return contrato;
    }

    public Optional<Condutor> getCondutor() {
        return Optional.ofNullable(condutor);
    }

    public LocalDate getData() {
        return data;
    }

    public int getKmInicial() {
        return kmInicial;
    }

    public int getKmFinal() {
        return kmFinal;
    }

    public String getOrigem() {
        return origem;
    }

    public String getDestino() {
        return destino;
    }

    public String getObservacao() {
        return observacao;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof RegistroDeKm registro)) {
            return false;
        }
        return id != null && id.equals(registro.id);
    }

    @Override
    public int hashCode() {
        return RegistroDeKm.class.hashCode();
    }
}
