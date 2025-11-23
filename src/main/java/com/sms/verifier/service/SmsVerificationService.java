package com.sms.verifier.service;

import com.sms.grpc.SmsPayload;
import com.sms.grpc.SmsRequest;
import com.sms.grpc.SmsVerificationResponse;
import com.sms.grpc.VerificationStatus;
import com.sms.verifier.util.SignatureVerificationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for verifying digital signatures on SMS requests
 * This service only handles verification operations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmsVerificationService {

    private final SignatureVerificationUtil signatureVerificationUtil;

    /**
     * Verifies the signature of an SMS request and returns detailed response
     *
     * @param smsRequest The SMS request containing payload and signature
     * @return SmsVerificationResponse with verification result and status
     */
    public SmsVerificationResponse verifyRequest(SmsRequest smsRequest) {
        try {
            // Validate request structure
            if (smsRequest == null) {
                log.warn("Received null SMS request");
                return buildVerificationResponse(null, false,
                    "Request is null", VerificationStatus.VERIFICATION_ERROR);
            }

            if (smsRequest.getPayload() == null) {
                log.warn("SMS request payload is null");
                return buildVerificationResponse(null, false,
                    "Payload is missing", VerificationStatus.MISSING_PAYLOAD);
            }

            if (smsRequest.getMessageSignature() == null || smsRequest.getMessageSignature().isEmpty()) {
                log.warn("SMS request signature is missing or empty for message ID: {}",
                    smsRequest.getPayload().getMessageId());
                return buildVerificationResponse(
                    smsRequest.getPayload().getMessageId(),
                    false,
                    "Signature is missing or empty",
                    VerificationStatus.MISSING_SIGNATURE);
            }

            // Extract payload and signature
            SmsPayload payload = smsRequest.getPayload();
            String signature = smsRequest.getMessageSignature();
            String messageId = payload.getMessageId();

            log.debug("Verifying signature for message ID: {}", messageId);

            // Verify the signature using SignatureVerificationUtil
            boolean isValid = signatureVerificationUtil.verifySignature(payload, signature);

            if (isValid) {
                log.info("Signature verification successful for message ID: {}", messageId);
                return buildVerificationResponse(
                    messageId,
                    true,
                    "Signature is valid",
                    VerificationStatus.VALID);
            } else {
                log.warn("Signature verification failed for message ID: {}", messageId);
                return buildVerificationResponse(
                    messageId,
                    false,
                    "Signature is invalid",
                    VerificationStatus.INVALID_SIGNATURE);
            }

        } catch (Exception e) {
            log.error("Error verifying signature: {}", e.getMessage(), e);
            String messageId = smsRequest != null && smsRequest.getPayload() != null
                ? smsRequest.getPayload().getMessageId()
                : "unknown";
            return buildVerificationResponse(
                messageId,
                false,
                "Verification error: " + e.getMessage(),
                VerificationStatus.VERIFICATION_ERROR);
        }
    }

    /**
     * Simplified verification that returns only boolean result
     *
     * @param smsRequest The SMS request containing payload and signature
     * @return true if the signature is valid, false otherwise
     */
    public boolean isSignatureValid(SmsRequest smsRequest) {
        SmsVerificationResponse response = verifyRequest(smsRequest);
        return response.getVerified();
    }

    /**
     * Helper method to build verification response
     *
     * @param messageId The message ID
     * @param verified Whether the signature is verified
     * @param statusMessage The status message
     * @param status The verification status enum
     * @return SmsVerificationResponse
     */
    private SmsVerificationResponse buildVerificationResponse(
            String messageId,
            boolean verified,
            String statusMessage,
            VerificationStatus status) {

        return SmsVerificationResponse.newBuilder()
                .setMessageId(messageId != null ? messageId : "unknown")
                .setVerified(verified)
                .setStatusMessage(statusMessage)
                .setVerifiedAt(System.currentTimeMillis())
                .setStatus(status)
                .build();
    }
}
