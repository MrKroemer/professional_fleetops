package br.com.proyfebrasil.fleetops.cadastros.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Canais de atendimento de uma locadora.
 *
 * <p>Agrupados em um objeto de valor por serem um conjunto coeso que sempre muda junto,
 * e para não inflar a assinatura dos métodos de {@link Locadora} com sete parâmetros de
 * texto indistinguíveis entre si.
 */
@Embeddable
public class CanaisDeAtendimento {

    @Column(name = "canal_reservas", length = 200)
    private String reservas;

    @Column(name = "canal_manutencao", length = 200)
    private String manutencao;

    @Column(name = "canal_guincho", length = 200)
    private String guinchoSinistro;

    @Column(name = "canal_assistencia_24h", length = 200)
    private String assistencia24h;

    @Column(name = "canal_financeiro", length = 200)
    private String financeiro;

    @Column(name = "canal_suporte", length = 200)
    private String suporte;

    @Column(name = "canal_telemetria", length = 200)
    private String telemetria;

    /** Construtor exigido pelo JPA. */
    public CanaisDeAtendimento() {
    }

    public CanaisDeAtendimento(
            String reservas,
            String manutencao,
            String guinchoSinistro,
            String assistencia24h,
            String financeiro,
            String suporte,
            String telemetria) {
        this.reservas = reservas;
        this.manutencao = manutencao;
        this.guinchoSinistro = guinchoSinistro;
        this.assistencia24h = assistencia24h;
        this.financeiro = financeiro;
        this.suporte = suporte;
        this.telemetria = telemetria;
    }

    public String getReservas() {
        return reservas;
    }

    public String getManutencao() {
        return manutencao;
    }

    public String getGuinchoSinistro() {
        return guinchoSinistro;
    }

    public String getAssistencia24h() {
        return assistencia24h;
    }

    public String getFinanceiro() {
        return financeiro;
    }

    public String getSuporte() {
        return suporte;
    }

    public String getTelemetria() {
        return telemetria;
    }
}
