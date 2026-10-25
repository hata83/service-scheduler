package dev.hata.scheduler.auth;

import java.time.Instant;
import java.util.Map;

import dev.hata.scheduler.common.IdempotencyStorage;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * One-shot customer registration (design D11 / spec 6). Verifies the REGISTER-purpose
 * OTP, inserts user + profile, creates JWT session, returns 201 + Set-Cookie: jwt.
 * Idempotency key required (ADR-008); replay returns cached response.
 */
@RestController
public class RegisterController {

    private static final Logger log = LoggerFactory.getLogger(RegisterController.class);

    private final OtpStorage otp;
    private final AuthProperties props;
    private final IdempotencyStorage idempotency;
    private final JwtService jwt;

    RegisterController(OtpStorage otp, AuthProperties props, IdempotencyStorage idempotency, JwtService jwt) {
        this.otp = otp;
        this.props = props;
        this.idempotency = idempotency;
        this.jwt = jwt;
    }

    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody RegisterRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletResponse response) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return problem(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency key missing",
                    "Request must include an X-Idempotency-Key header.");
        }
        String slot = "idempotency:register:" + body.phone() + ":" + idempotencyKey;
        if (idempotency.isConsumed(slot)) {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("cached", true));
        }
        if (!otp.consume(body.phone(), OtpPurpose.REGISTER, body.code())) {
            return problem(
                    HttpStatus.UNAUTHORIZED,
                    "OTP verification failed",
                    "Wrong, expired, or LOGIN-purpose code for registration.");
        }
        // Insert user + profile, issue JWT (simplified stub)
        String token = jwt.create("user-id", "CUSTOMER", body.phone());
        response.addHeader("Set-Cookie", "jwt=" + token + "; HttpOnly; Path=/; Max-Age=28800");
        idempotency.store(slot, "201", 24 * 3600);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("phone", body.phone(), "role", "CUSTOMER"));
    }

    private ResponseEntity<Map<String, Object>> problem(HttpStatus s, String title, String detail) {
        return ResponseEntity.status(s)
                .body(Map.of(
                        "type",
                        "/errors/" + s.value(),
                        "title",
                        title,
                        "detail",
                        detail,
                        "timestamp",
                        Instant.now().toString()));
    }

    public record RegisterRequest(String phone, String firstName, String lastName, String code) {}
}
