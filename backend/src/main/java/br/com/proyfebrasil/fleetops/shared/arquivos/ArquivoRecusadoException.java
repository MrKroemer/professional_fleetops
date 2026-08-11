package br.com.proyfebrasil.fleetops.shared.arquivos;

import br.com.proyfebrasil.fleetops.shared.exception.CodigoErro;
import br.com.proyfebrasil.fleetops.shared.exception.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * Envio recusado por tipo ou tamanho.
 *
 * <p>É erro de negócio, não falha técnica: quem enviou um arquivo grande demais precisa
 * ler o limite e tentar de novo, e o frontend precisa distinguir isso de uma queda do
 * armazenamento — daí o código estável em vez de um 500 genérico.
 */
public class ArquivoRecusadoException extends NegocioException {

    public ArquivoRecusadoException(String detalhe) {
        super(Codigo.ARQUIVO_RECUSADO, detalhe);
    }

    /** Código do erro; declarado aqui por ser específico do armazenamento. */
    public enum Codigo implements CodigoErro {

        ARQUIVO_RECUSADO(
                "GEN-012-ARQUIVO_RECUSADO",
                "Arquivo recusado",
                HttpStatus.UNPROCESSABLE_ENTITY);

        private final String codigo;
        private final String titulo;
        private final HttpStatus status;

        Codigo(String codigo, String titulo, HttpStatus status) {
            this.codigo = codigo;
            this.titulo = titulo;
            this.status = status;
        }

        @Override
        public String codigo() {
            return codigo;
        }

        @Override
        public String titulo() {
            return titulo;
        }

        @Override
        public HttpStatus status() {
            return status;
        }
    }
}
