package com.sms.verifier.controller;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.sms.grpc.SmsRequest;
import com.sms.grpc.SmsVerificationResponse;
import com.sms.verifier.service.SmsVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for SMS signature verification operations
 * This controller only handles verification endpoints (no signing)
 */
@RestController
@RequestMapping("/api/v1/sms")
@Slf4j
@RequiredArgsConstructor
public class SmsVerifierController {

    private final SmsVerificationService smsVerificationService;

    /**
     * Verifies a signed SMS request
     *
     * @param requestJson JSON representation of SmsRequest
     * @return Verification result with detailed status
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifySmsSignature(@RequestBody String requestJson) {
        log.info("Received SMS signature verification request");

        try {
            // Parse JSON to protobuf SmsRequest
            SmsRequest.Builder builder = SmsRequest.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(requestJson, builder);
            SmsRequest smsRequest = builder.build();

            // Verify the signature through service layer
            SmsVerificationResponse verificationResponse = smsVerificationService.verifyRequest(smsRequest);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("messageId", verificationResponse.getMessageId());
            response.put("verified", verificationResponse.getVerified());
            response.put("status", verificationResponse.getStatus().name());
            response.put("statusMessage", verificationResponse.getStatusMessage());
            response.put("verifiedAt", verificationResponse.getVerifiedAt());

            HttpStatus httpStatus = verificationResponse.getVerified() ? HttpStatus.OK : HttpStatus.UNAUTHORIZED;

            log.info("Verification result for message {}: {} - {}",
                    verificationResponse.getMessageId(),
                    verificationResponse.getVerified(),
                    verificationResponse.getStatus());

            return ResponseEntity.status(httpStatus).body(response);

        } catch (InvalidProtocolBufferException e) {
            log.error("Invalid request format: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "INVALID_REQUEST_FORMAT");
            errorResponse.put("message", "Failed to parse SMS request");
            errorResponse.put("details", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            log.error("Error processing verification request: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "VERIFICATION_ERROR");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Simple verification endpoint that returns just true/false
     *
     * @param requestJson JSON representation of SmsRequest
     * @return Simple boolean verification result
     */
    @PostMapping("/verify/simple")
    public ResponseEntity<Map<String, Object>> simpleVerify(@RequestBody String requestJson) {
        log.info("Received simple SMS verification request");

        try {
            // Parse JSON to protobuf SmsRequest
            SmsRequest.Builder builder = SmsRequest.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(requestJson, builder);
            SmsRequest smsRequest = builder.build();

            // Verify the signature
            boolean isValid = smsVerificationService.isSignatureValid(smsRequest);

            Map<String, Object> response = new HashMap<>();
            response.put("verified", isValid);
            response.put("messageId", smsRequest.getPayload().getMessageId());
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in simple verification: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("verified", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "SMS Verifier Service");
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }

    /**
     * Service info endpoint
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "SMS Verifier Service");
        response.put("description", "Digital signature verification for SMS/Notification platforms");
        response.put("version", "1.0.0");
        response.put("capabilities", new String[]{"signature-verification"});
        response.put("endpoints", Map.of(
            "verify", "/api/v1/sms/verify",
            "simpleVerify", "/api/v1/sms/verify/simple",
            "health", "/api/v1/sms/health"
        ));
        return ResponseEntity.ok(response);
    }
}
