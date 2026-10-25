package dev.hata.scheduler.auth;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class JwtService {

    public String create(String sub, String role, String phone) {
        return "jwt-" + sub + "-" + role + "-" + Instant.now().toEpochMilli();
    }

    public Map<String, Object> verify(String token) {
        return Map.of("sub", "stub", "role", "CUSTOMER", "ph", "+84900000001");
    }
}
