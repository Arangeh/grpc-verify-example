# SMS Verifier Service

A Spring Boot application that provides gRPC-based signature verification for SMS messages signed by the Signer Service.

## Overview

This service is the verification counterpart to the SMS Signer Service. It:
1. Receives signed SMS requests via gRPC from the Signer Service
2. Verifies the digital signatures using the RSA public key
3. Returns verification results back to the Signer Service

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client                                          │
│                                │                                             │
│                    POST /api/v1/sms/sign                                     │
│                         (JSON payload)                                       │
│                                ▼                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    SMS Signer Service (Port 8080)                     │   │
│  │                                                                       │   │
│  │   1. Receive SMS payload                                              │   │
│  │   2. Sign with RSA private key                                        │   │
│  │   3. Send to Verifier via gRPC ─────────────────┐                     │   │
│  │   4. Return result                               │                     │   │
│  └──────────────────────────────────────────────────┼─────────────────────┘  │
│                                                     │                         │
│                                              gRPC (Port 9091)                │
│                                                     │                         │
│                                                     ▼                         │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                  SMS Verifier Service (Port 8081/9091)  <── YOU ARE HERE │
│  │                                                                       │   │
│  │   - Receives signed request via gRPC                                  │   │
│  │   - Verifies signature with RSA public key                            │   │
│  │   - Returns verification result                                       │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Technology Stack

- **Java 21**
- **Spring Boot 3.4.4**
- **Spring gRPC 0.9.0** (Official Spring gRPC)
- **gRPC 1.68.0**
- **Protocol Buffers 4.28.2**
- **Maven** for dependency management
- **Lombok** for reducing boilerplate code

## Project Structure

```
grpc-verify-example/
├── src/
│   ├── main/
│   │   ├── java/com/sms/verifier/
│   │   │   ├── controller/
│   │   │   │   └── SmsVerifierController.java      # REST endpoints (optional)
│   │   │   ├── grpc/
│   │   │   │   └── SmsVerificationGrpcService.java # gRPC service implementation
│   │   │   ├── service/
│   │   │   │   └── SmsVerificationService.java     # Business logic
│   │   │   ├── util/
│   │   │   │   └── SignatureVerificationUtil.java  # Crypto operations
│   │   │   └── SmsVerifierApplication.java         # Main application
│   │   ├── proto/
│   │   │   └── sms_service.proto                   # gRPC service definition
│   │   └── resources/
│   │       ├── application.yml                     # Configuration
│   │       └── keys/
│   │           └── public_key.pem                  # RSA public key (verification only)
│   └── test/
└── pom.xml
```

## Key Components

### gRPC Service

- **SmsVerificationGrpcService**: Implements `SmsVerificationService` from the proto definition
  - `verifySms(SmsRequest)`: Verifies a single signed SMS request
  - `verifyBulkSms(stream SmsRequest)`: Verifies bulk SMS requests via streaming

Uses the official Spring gRPC annotation:
```java
@org.springframework.grpc.server.service.GrpcService
```

### Service Layer

- **SmsVerificationService**: Contains business logic for signature verification

### Utility Layer

- **SignatureVerificationUtil**: Handles RSA signature verification using the public key

### REST Endpoints (Optional)

The REST endpoints are still available for direct testing:
- `POST /api/v1/sms/verify`: Verify a signed request
- `POST /api/v1/sms/verify/simple`: Simple true/false verification
- `GET /api/v1/sms/health`: Health check
- `GET /api/v1/sms/info`: Service information

## Dependencies

This project uses the official **Spring gRPC** library:

```xml
<dependency>
    <groupId>org.springframework.grpc</groupId>
    <artifactId>spring-grpc-spring-boot-starter</artifactId>
</dependency>
```

With dependency management:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.grpc</groupId>
            <artifactId>spring-grpc-dependencies</artifactId>
            <version>0.9.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Building the Project

### Generate Protobuf Classes

```bash
mvn clean compile
```

### Build the Application

```bash
mvn clean package
```

## Running the Application

**Important**: This service must be started before the Signer Service.

```bash
# Terminal 1: Start Verifier Service first
cd grpc-verify-example
mvn spring-boot:run

# Terminal 2: Start Signer Service
cd ../grpc-sign-example
mvn spring-boot:run
```

The application will start:
- REST API: `http://localhost:8081`
- gRPC Server: `localhost:9091`

## gRPC Service Definition

From `sms_service.proto`:

```protobuf
service SmsVerificationService {
  // Verify a single signed SMS
  rpc VerifySms(SmsRequest) returns (SmsVerificationResponse);

  // Verify bulk SMS messages in stream
  rpc VerifyBulkSms(stream SmsRequest) returns (BulkVerificationResponse);
}
```

## Verification Status

The service returns detailed verification status:

| Status | Description |
|--------|-------------|
| `VALID` | Signature is valid |
| `INVALID_SIGNATURE` | Signature verification failed |
| `MISSING_SIGNATURE` | No signature provided |
| `MISSING_PAYLOAD` | Payload is missing |
| `VERIFICATION_ERROR` | Error during verification process |

## Testing with cURL (REST)

### Verify a signed request directly:

```bash
curl -X POST http://localhost:8081/api/v1/sms/verify \
  -H "Content-Type: application/json" \
  -d '{
    "payload": {
      "messageId": "msg-12345",
      "sender": "+1234567890",
      "recipient": "+0987654321",
      "content": "Hello, this is a test SMS",
      "timestamp": 1699564800000,
      "priority": "HIGH"
    },
    "messageSignature": "base64-signature-from-signer"
  }'
```

### Testing with grpcurl (gRPC)

```bash
# Install grpcurl if not already installed
# brew install grpcurl (macOS)
# go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest (Go)

# List available services
grpcurl -plaintext localhost:9091 list

# Verify a single SMS
grpcurl -plaintext -d '{
  "payload": {
    "messageId": "msg-12345",
    "sender": "+1234567890",
    "recipient": "+0987654321",
    "content": "Test message"
  },
  "messageSignature": "base64-signature"
}' localhost:9091 sms.SmsVerificationService/VerifySms
```

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: sms-verifier-service
  grpc:
    server:
      port: 9091  # gRPC server port

server:
  port: 8081  # REST API port

logging:
  level:
    com.sms.verifier: DEBUG  # Change to INFO in production
```

## Security Considerations

1. **Public Key Only**: This service only stores the public key, reducing security risks
2. **No Signing Capability**: Cannot create signatures, only verify them
3. **Stateless**: Each verification is independent
4. **Tamper Detection**: Any modification to the payload will cause verification to fail

## Differences from Signer Service

| Feature | Signer Service | Verifier Service |
|---------|---------------|------------------|
| REST Port | 8080 | 8081 |
| gRPC Role | Client | Server |
| gRPC Port | N/A (client) | 9091 |
| Keys Required | Private only | Public only |
| Capabilities | Sign | Verify |
| Main Protocol | REST (input) + gRPC (output) | gRPC (input/output) |

## Logging

The service provides comprehensive logging:
- `INFO`: Service startup, verification results
- `DEBUG`: Detailed verification steps, message sizes, hash lengths
- `WARN`: Invalid requests, failed verifications
- `ERROR`: Exceptions and errors

## License

This is a sample project for educational purposes.
