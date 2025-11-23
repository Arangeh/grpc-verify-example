package com.sms.verifier.grpc;

import com.sms.grpc.SmsRequest;
import com.sms.grpc.SmsVerificationResponse;
import com.sms.grpc.SmsVerificationServiceGrpc;
import com.sms.grpc.BulkVerificationResponse;
import com.sms.verifier.service.SmsVerificationService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC Service implementation for SMS signature verification.
 * This service receives signed SMS requests from the Signer Service and verifies them.
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class SmsVerificationGrpcService extends SmsVerificationServiceGrpc.SmsVerificationServiceImplBase {

    private final SmsVerificationService smsVerificationService;

    /**
     * Verifies a single signed SMS request via gRPC
     *
     * @param request The signed SMS request containing payload and signature
     * @param responseObserver The response observer to send verification result
     */
    @Override
    public void verifySms(SmsRequest request, StreamObserver<SmsVerificationResponse> responseObserver) {
        log.info("Received gRPC verification request for message ID: {}",
                request.hasPayload() ? request.getPayload().getMessageId() : "unknown");

        try {
            // Delegate to the existing verification service
            SmsVerificationResponse response = smsVerificationService.verifyRequest(request);

            log.info("gRPC verification completed for message {}: verified={}",
                    response.getMessageId(), response.getVerified());

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error during gRPC verification: {}", e.getMessage(), e);
            responseObserver.onError(
                    io.grpc.Status.INTERNAL
                            .withDescription("Verification failed: " + e.getMessage())
                            .asRuntimeException()
            );
        }
    }

    /**
     * Verifies bulk SMS requests via gRPC streaming
     *
     * @param responseObserver The response observer to send bulk verification result
     * @return StreamObserver for receiving streaming requests
     */
    @Override
    public StreamObserver<SmsRequest> verifyBulkSms(StreamObserver<BulkVerificationResponse> responseObserver) {
        log.info("Starting bulk gRPC verification stream");

        return new StreamObserver<SmsRequest>() {
            private int totalProcessed = 0;
            private int verified = 0;
            private int failed = 0;
            private final java.util.List<String> failedMessageIds = new java.util.ArrayList<>();

            @Override
            public void onNext(SmsRequest request) {
                totalProcessed++;
                String messageId = request.hasPayload() ? request.getPayload().getMessageId() : "unknown";

                try {
                    SmsVerificationResponse response = smsVerificationService.verifyRequest(request);

                    if (response.getVerified()) {
                        verified++;
                        log.debug("Bulk verification: message {} verified", messageId);
                    } else {
                        failed++;
                        failedMessageIds.add(messageId);
                        log.debug("Bulk verification: message {} failed", messageId);
                    }
                } catch (Exception e) {
                    failed++;
                    failedMessageIds.add(messageId);
                    log.warn("Bulk verification error for message {}: {}", messageId, e.getMessage());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error in bulk verification stream: {}", t.getMessage(), t);
            }

            @Override
            public void onCompleted() {
                BulkVerificationResponse response = BulkVerificationResponse.newBuilder()
                        .setTotalProcessed(totalProcessed)
                        .setVerified(verified)
                        .setFailed(failed)
                        .addAllFailedMessageIds(failedMessageIds)
                        .build();

                log.info("Bulk gRPC verification completed: total={}, verified={}, failed={}",
                        totalProcessed, verified, failed);

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }
}
