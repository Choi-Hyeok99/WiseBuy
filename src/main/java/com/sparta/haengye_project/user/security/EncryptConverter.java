package com.sparta.haengye_project.user.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptConverter implements AttributeConverter<String, String> {

    private static final String SECRET_KEY = "1234567890123456";  // AES-128 비밀 키

    @Override
    public String convertToDatabaseColumn(String attribute) {
        try {
            return AESUtil.encrypt(attribute, SECRET_KEY);  // 암호화
        } catch (Exception e) {
            throw new IllegalArgumentException("Encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        try {
            return AESUtil.decrypt(dbData, SECRET_KEY);  // 복호화
        } catch (Exception e) {
            throw new IllegalArgumentException("Decryption failed", e);
        }
    }
}