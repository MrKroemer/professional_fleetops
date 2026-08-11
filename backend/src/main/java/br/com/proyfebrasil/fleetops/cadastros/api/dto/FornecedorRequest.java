package br.com.proyfebrasil.fleetops.cadastros.api.dto;

import br.com.proyfebrasil.fleetops.cadastros.domain.DiaDaSemana;
import br.com.proyfebrasil.fleetops.cadastros.domain.TipoFornecedor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Dados de criação ou atualização de um fornecedor credenciado.
 *
 * <p>Apenas o bloco correspondente ao {@code tipo} deve ser preenchido. Enviar dados de
 * outro tipo resulta em erro, e não em descarte silencioso.
 */
@Schema(name = "FornecedorRequest", description = "Dados de um fornecedor credenciado")
public record FornecedorRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Informe o tipo do fornecedor")
        TipoFornecedor tipo,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Informe o nome do fornecedor")
        @Size(max = 180)
        String nome,

        @Size(max = 120) String cidade,

        @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "A UF deve ter exatamente duas letras")
        String uf,

        @Size(max = 300) String endereco,
        @Size(max = 120) String telefone,

        @Email(message = "Informe um e-mail válido")
        @Size(max = 180)
        String email,

        @Size(max = 160) String responsavel,
        @Size(max = 200) String funcionamento,
        @Size(max = 200) String formaFaturamento,
        @Size(max = 200) String formaPagamento,
        LocalDate credenciadoEm,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ativo,
        @Size(max = 2000) String observacoes,

        @Schema(description = "Obras atendidas por este fornecedor")
        List<Long> obrasIds,

        @Valid DadosDePostoDto posto,
        @Valid DadosDeLavaJatoDto lavaJato,
        @Valid DadosDeRastreadorDto rastreador,
        @Valid DadosDeGraficaDto grafica) {

    /** Dados exclusivos de posto: dias em que o abastecimento é autorizado (RN-04). */
    @Schema(name = "DadosDePosto", description = "Dados específicos de um posto")
    public record DadosDePostoDto(
            @Schema(description = "Vazio significa sem restrição de dia")
            Set<DiaDaSemana> diasAutorizados,
            @Size(max = 120) String acessoFaturas) {
    }

    /** Dados exclusivos de lava-jato: frequência (RN-05) e preços por categoria. */
    @Schema(name = "DadosDeLavaJato", description = "Dados específicos de um lava-jato")
    public record DadosDeLavaJatoDto(
            @Min(value = 1, message = "A frequência deve ser de ao menos 1 serviço por semana")
            @Max(value = 7, message = "A frequência não pode passar de 7 serviços por semana")
            Integer servicosPorSemana,

            @DecimalMin(value = "0.00", message = "O preço não pode ser negativo") BigDecimal precoPasseio,
            @DecimalMin(value = "0.00", message = "O preço não pode ser negativo") BigDecimal precoSuv,
            @DecimalMin(value = "0.00", message = "O preço não pode ser negativo") BigDecimal precoQuatroXQuatro) {
    }

    /**
     * Dados exclusivos de rastreador.
     *
     * <p>Sobre as credenciais (RN-20): omitir preserva o valor atual; vazio remove.
     */
    @Schema(name = "DadosDeRastreador", description = "Dados específicos de um fornecedor de rastreamento")
    public record DadosDeRastreadorDto(
            @DecimalMin(value = "0.00", message = "O valor não pode ser negativo") BigDecimal mensalidade,
            @DecimalMin(value = "0.00", message = "O valor não pode ser negativo") BigDecimal custoInstalacao,
            @DecimalMin(value = "0.00", message = "O valor não pode ser negativo") BigDecimal custoDesinstalacao,
            @Size(max = 180) String equipadora,
            @Size(max = 400) String portalUrl,
            @Size(max = 200) String portalLogin,
            @Schema(format = "password") @Size(max = 200) String portalSenha) {
    }

    /** Dados exclusivos de gráfica: tamanhos e preços de adesivo e imã. */
    @Schema(name = "DadosDeGrafica", description = "Dados específicos de uma gráfica")
    public record DadosDeGraficaDto(
            @Size(max = 40) String tamanhoAdesivo,
            @DecimalMin(value = "0.00", message = "O preço não pode ser negativo") BigDecimal precoAdesivo,
            @Size(max = 40) String tamanhoIma,
            @DecimalMin(value = "0.00", message = "O preço não pode ser negativo") BigDecimal precoIma) {
    }
}
