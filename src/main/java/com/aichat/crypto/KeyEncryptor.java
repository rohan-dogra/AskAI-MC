package com.aichat.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

public final class KeyEncryptor {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final String SALT_FILE = ".salt";

    private final SecretKey secretKey;

    public KeyEncryptor(String configSeed, File dataFolder) {
        try {
            byte[] salt = loadOrCreateSalt(dataFolder);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(configSeed.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS);
            this.secretKey = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize encryption", e);
        }
    }

    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[IV_BYTES];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, combined, IV_BYTES, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new RuntimeException("Decryption failed. key may have been corrupted or encryption seed changed", e);
        }
    }

    private static byte[] loadOrCreateSalt(File dataFolder) {
        File saltFile = new File(dataFolder, SALT_FILE);
        if (saltFile.exists()) {
            try {
                byte[] salt = Files.readAllBytes(saltFile.toPath());
                if (salt.length == 16) return salt;
            } catch (IOException ignored) {
            }
        }
        byte[] salt = new byte[16];
        try {
            SecureRandom.getInstanceStrong().nextBytes(salt);
        } catch (GeneralSecurityException e) {
            new SecureRandom().nextBytes(salt);
        }
        try {
            dataFolder.mkdirs();
            Files.write(saltFile.toPath(), salt);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write salt file", e);
        }
        return salt;
    }
}
