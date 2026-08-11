package br.com.proyfebrasil.fleetops.contratos.domain;

import br.com.proyfebrasil.fleetops.shared.arquivos.Anexo;
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
import java.util.Objects;

/** Uma foto do book, presa ao ângulo que ela documenta. */
@Entity
@Table(name = "foto_do_book")
public class FotoDoBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private EventoDeContrato evento;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "anexo_id", nullable = false)
    private Anexo anexo;

    @Enumerated(EnumType.STRING)
    @Column(name = "item", length = 30, nullable = false)
    private ItemDoBook item;

    @Column(name = "observacao", length = 300)
    private String observacao;

    /** Construtor exigido pelo JPA. */
    protected FotoDoBook() {
    }

    FotoDoBook(EventoDeContrato evento, ItemDoBook item, Anexo anexo, String observacao) {
        this.evento = Objects.requireNonNull(evento, "evento é obrigatório");
        this.item = Objects.requireNonNull(item, "item do book é obrigatório");
        this.anexo = Objects.requireNonNull(anexo, "anexo é obrigatório");
        if (!anexo.ehImagem()) {
            throw new IllegalArgumentException(
                    "O book fotográfico aceita apenas imagens; %s é %s."
                            .formatted(anexo.getNomeOriginal(), anexo.getTipoDeConteudo()));
        }
        this.observacao = observacao;
    }

    public Long getId() {
        return id;
    }

    public EventoDeContrato getEvento() {
        return evento;
    }

    public Anexo getAnexo() {
        return anexo;
    }

    public ItemDoBook getItem() {
        return item;
    }

    public String getObservacao() {
        return observacao;
    }
}
