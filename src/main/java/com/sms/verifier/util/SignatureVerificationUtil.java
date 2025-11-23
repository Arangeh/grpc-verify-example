package com.sms.verifier.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Utility class for verifying digital signatures on SMS messages
 * This component only handles signature verification (no signing capability)
 */
@Component
@Slf4j
public class SignatureVerificationUtil {

    private static final String SIGNING_ALGORITHM = "SHA256withRSA";
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private PublicKey globalPublicKey;

    /**
     * Initialize the public key when the component is created
     * Only loads the public key since this service is for verification only
     */
    @PostConstruct
    public void initializeKeys() {
        try {
            this.globalPublicKey = loadPublicKey();

            log.info("Global RSA public key initialized successfully for verification");
            log.debug("Public Key (Base64): {}", keyToBase64(globalPublicKey));

        } catch (Exception e) {
            log.error("Failed to initialize public key", e);
            throw new RuntimeException("Could not initialize verification keys", e);
        }
    }

    /**
     * Verifies a protobuf message signature using RS256 algorithm with global public key
     *
     * @param message The protobuf message to verify
     * @param signatureBase64 The Base64 encoded signature to verify
     * @return true if signature is valid, false otherwise
     */
    public boolean verifySignature(com.google.protobuf.Message message, String signatureBase64) {
        try {
            if (globalPublicKey == null) {
                throw new IllegalStateException("Global public key not initialized");
            }

            if (message == null) {
                log.warn("Message is null, cannot verify");
                return false;
            }

            if (signatureBase64 == null || signatureBase64.isEmpty()) {
                log.warn("Signature is null or empty, cannot verify");
                return false;
            }

            // Convert message to canonical binary representation
            byte[] messageBytes = message.toByteArray();

            // Compute digest using SHA-256
            MessageDigest digester = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] hashedMessage = digester.digest(messageBytes);

            log.debug("Message bytes length: {}, Hash length: {}", messageBytes.length, hashedMessage.length);

            // Decode the signature from Base64
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);

            // Verify the signature using RS256 (SHA256withRSA)
            Signature signature = Signature.getInstance(SIGNING_ALGORITHM);
            signature.initVerify(globalPublicKey);
            signature.update(hashedMessage);

            boolean isValid = signature.verify(signatureBytes);
            log.debug("Signature verification result: {}", isValid);

            return isValid;

        } catch (Exception e) {
            log.error("Error during signature verification: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Loads the public key from the classpath
     *
     * @return The loaded public key
     * @throws Exception if the key cannot be loaded
     */
    private PublicKey loadPublicKey() throws Exception {
        try (InputStream publicKeyStream = getClass().getClassLoader().getResourceAsStream("keys/public_key.pem")) {
            if (publicKeyStream == null) {
                throw new RuntimeException("Public key file not found in classpath: keys/public_key.pem");
            }

            String pemContent = new String(publicKeyStream.readAllBytes(), StandardCharsets.UTF_8);
            String publicKeyPEM = pemContent
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyPEM);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            return keyFactory.generatePublic(publicKeySpec);
        }
    }

    /**
     * Converts key to Base64 for logging/debugging
     *
     * @param key The key to convert
     * @return Base64 encoded key
     */
    public String keyToBase64(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Get the global public key
     *
     * @return The global public key
     */
    public PublicKey getGlobalPublicKey() {
        return globalPublicKey;
    }
}
