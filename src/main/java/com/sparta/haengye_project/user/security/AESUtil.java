package com.sparta.haengye_project.user.security;


import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class AESUtil {

    private static final String ALGORITHM = "AES";  // 사용할 암호화 알고리즘

    // 데이터 암호화
    public static String encrypt(String data, String secretKey) throws Exception {
        SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedData = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    // 데이터 복호화
    public static String decrypt(String encryptedData, String secretKey) throws Exception {
        SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptedData = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decryptedData);
    }
}