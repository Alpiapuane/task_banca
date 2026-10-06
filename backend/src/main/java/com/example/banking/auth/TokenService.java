package com.example.banking.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final ObjectMapper mapper;
    private final byte[] signingKey;
    private final long ttlSeconds;

    public TokenService(ObjectMapper mapper,
                        @Value("${app.auth.signing-secret:}") String configuredSecret,
                        @Value("${app.auth.token-ttl-minutes:60}") long ttlMinutes) {
        this.mapper = mapper;
        this.signingKey = configuredSecret == null || configuredSecret.isBlank()
                ? randomKey() : configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (signingKey.length < 32) throw new IllegalArgumentException("JWT_SIGNING_SECRET must be at least 32 bytes");
        this.ttlSeconds = ttlMinutes * 60;
    }

    public String issue(String username) {
        try {
            String header = encode(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = encode(mapper.writeValueAsBytes(Map.of("sub", username, "exp", Instant.now().getEpochSecond() + ttlSeconds)));
            String content = header + "." + payload;
            return content + "." + encode(sign(content));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to issue access token", ex);
        }
    }
    public long getTtlSeconds() { return ttlSeconds; }

    public String validateAndGetSubject(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !java.security.MessageDigest.isEqual(sign(parts[0] + "." + parts[1]),
                    Base64.getUrlDecoder().decode(parts[2]))) return null;
            Map<?, ?> claims = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            Object subject = claims.get("sub");
            Object expiry = claims.get("exp");
            if (!(subject instanceof String username) || !(expiry instanceof Number exp)
                    || exp.longValue() <= Instant.now().getEpochSecond()) return null;
            return username;
        } catch (Exception ex) {
            return null;
        }
    }

    private byte[] sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
    private static String encode(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
