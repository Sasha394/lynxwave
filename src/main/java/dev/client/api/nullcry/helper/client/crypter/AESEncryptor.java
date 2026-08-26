package dev.client.api.nullcry.helper.client.crypter;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AESEncryptor {
    private static final String SECRET_KEY = "qh/Koq3Te7QjXIDpThzwBg==";
    private static final byte[] INIT_VECTOR = new byte[16];

    public static String crypt(String text) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(Base64.getDecoder().decode(SECRET_KEY), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(INIT_VECTOR);

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] encryptedBytes = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public static String uncrypt(String encryptedText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(Base64.getDecoder().decode(SECRET_KEY), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(INIT_VECTOR);

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    public static String encrypt(String text) {
        try {
            return crypt(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String decrypt(String text) {
        try {
            return uncrypt(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}