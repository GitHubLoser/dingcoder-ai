package com.codereview.plugin.utils;



import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


public class AESUtils {

    private static final String IV_STRING = "ghUb#er57HBh(u%g";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";


    public static String aesEncryptByBase64(String src, String aesKey) {
        try {
            SecretKeySpec key = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
            byte[] initParam = IV_STRING.getBytes(StandardCharsets.UTF_8);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(initParam);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(1, key, ivParameterSpec);
            byte[] cleartext = src.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertextBytes = cipher.doFinal(cleartext);
            Base64.Encoder encoder = Base64.getEncoder();
            return encoder.encodeToString(ciphertextBytes);
        } catch (Exception ex) {
            return src;
        }
    }
}
