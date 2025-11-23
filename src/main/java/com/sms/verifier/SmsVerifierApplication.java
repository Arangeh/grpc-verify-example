package com.sms.verifier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Main Spring Boot Application for SMS Verifier Service
 *
 * This service provides digital signature verification capabilities for SMS/Notification platforms
 * supporting bulk message processing with streaming via gRPC.
 *
 * This is the verification counterpart to the SMS Signer Service.
 */

@SpringBootApplication
@Slf4j
public class SmsVerifierApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SmsVerifierApplication.class, args);

        log.info("==============================================");
        log.info("SMS Verifier Service Started Successfully");
        log.info("==============================================");
        log.info("REST API available at: http://localhost:8081/api/v1/sms");
        log.info("gRPC Server listening on port: 9091");
        log.info("Health check: http://localhost:8081/api/v1/sms/health");
        log.info("Service info: http://localhost:8081/api/v1/sms/info");
        log.info("==============================================");
        log.info("Endpoints:");
        log.info("  - POST /api/v1/sms/verify        - Verify signed SMS (detailed response)");
        log.info("  - POST /api/v1/sms/verify/simple - Verify signed SMS (simple response)");
        log.info("  - GET  /api/v1/sms/health        - Health check");
        log.info("  - GET  /api/v1/sms/info          - Service information");
        log.info("==============================================");
    }
}
