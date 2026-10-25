package dev.hata.scheduler.auth;

import java.util.Map;

import dev.hata.scheduler.common.IdempotencyStorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerifyController {

    private static final Logger log = LoggerFactory.getLogger(VerifyController.class);

    private final OtpStorage otp;
    private final IdempotencyStorage idempotency;
    private final JwtService jwt;

    VerifyController(OtpStorage otp, IdempotencyStorage idempotency, JwtService jwt) {
        this.otp = otp;
        this.idempotency = idempotency;
        this.jwt = jwt;
    }

    @PostMapping("/api/v1/auth/otp/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestBody VerifyRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "type",
                            "/errors/400",
                            "title",
                            "Idempotency key missing",
                            "detail",
                            "Request must include an X-Idempotency-Key header."));
        }
        String slot = "idempotency:otp:verify:" + body.phone() + ":" + idempotencyKey;
        if (idempotency.isConsumed(slot)) {
            return ResponseEntity.ok(Map.of("cached", true));
        }
        boolean matched = otp.consume(body.phone(), OtpPurpose.LOGIN, body.code());
        if (!matched) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "type",
                            "/errors/401",
                            "title",
                            "OTP verification failed",
                            "detail",
                            "Wrong or expired LOGIN-purpose code."));
        }
        String token = jwt.create(body.phone(), "CUSTOMER", body.phone());
        idempotency.store(slot, "ok", 24 * 3600);
        return ResponseEntity.ok(Map.of("token", token, "phone", body.phone()));
    }

    public record VerifyRequest(String phone, String code) {}
}
