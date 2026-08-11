package br.com.proyfebrasil.fleetops.shared.security;

import br.com.proyfebrasil.fleetops.shared.config.FleetOpsProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Cifra e decifra credenciais de portais de locadoras e rastreadores (RN-20).
 *
 * <p>AES-256-GCM com IV aleatório de 96 bits por operação. O IV é concatenado ao texto
 * cifrado e o conjunto é gravado em Base64 — assim o mesmo segredo cifrado duas vezes
 * produz resultados diferentes, e a autenticação do GCM detecta qualquer adulteração
 * do valor em banco.
 *
 * <p>A chave vem exclusivamente de {@code FLEETOPS_CRYPTO_KEY}. Uma chave ausente ou de
 * tamanho incorreto impede a subida da aplicação, em vez de degradar silenciosamente
 * para armazenamento em claro.
 */
@Component
public class CriptografiaCredenciais {

    /**
     * Máscara exibida no lugar de uma credencial existente.
     *
     * <p>Comprimento fixo de propósito: uma máscara proporcional ao tamanho real vazaria
     * o comprimento da senha, que é informação útil para quem tenta adivinhá-la.
     */
    public static final String MASCARA_PADRAO = "••••••••";

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV_BYTES = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final int TAMANHO_CHAVE_BYTES = 32;

    private final SecretKey chave;
    private final SecureRandom aleatorio = new SecureRandom();

    public CriptografiaCredenciais(FleetOpsProperties propriedades) {
        byte[] material = decodificarChave(propriedades.cripto().chave());
        if (material.length != TAMANHO_CHAVE_BYTES) {
            throw new IllegalStateException(
                    "FLEETOPS_CRYPTO_KEY deve conter exatamente 32 bytes (256 bits) em Base64; "
                            + "gere com: openssl rand -base64 32");
        }
        this.chave = new SecretKeySpec(material, "AES");
    }

    /** Cifra um valor em claro. {@code null} permanece {@code null}. */
    public String cifrar(String valorEmClaro) {
        if (valorEmClaro == null) {
            return null;
        }
        try {
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            aleatorio.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(valorEmClaro.getBytes(StandardCharsets.UTF_8));

            byte[] resultado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(cifrado, 0, resultado, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(resultado);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao cifrar credencial", ex);
        }
    }

    /** Decifra um valor previamente cifrado por {@link #cifrar(String)}. */
    public String decifrar(String valorCifrado) {
        if (valorCifrado == null) {
            return null;
        }
        try {
            byte[] bruto = Base64.getDecoder().decode(valorCifrado);
            if (bruto.length <= TAMANHO_IV_BYTES) {
                throw new IllegalArgumentException("Valor cifrado malformado");
            }
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            System.arraycopy(bruto, 0, iv, 0, TAMANHO_IV_BYTES);
            byte[] cifrado = new byte[bruto.length - TAMANHO_IV_BYTES];
            System.arraycopy(bruto, TAMANHO_IV_BYTES, cifrado, 0, cifrado.length);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Falha ao decifrar credencial: valor inválido ou chave incorreta", ex);
        }
    }


    /** Devolve a máscara adequada conforme exista ou não credencial armazenada (RN-20). */
    public static String mascaraPara(boolean possuiCredencial) {
        return possuiCredencial ? MASCARA_PADRAO : "";
    }

    private static byte[] decodificarChave(String chaveBase64) {
        try {
            return Base64.getDecoder().decode(chaveBase64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("FLEETOPS_CRYPTO_KEY não está em Base64 válido", ex);
        }
    }
}
