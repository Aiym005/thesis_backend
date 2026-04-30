package com.tms.thesissystem.application.service.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.thesissystem.api.ApiDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtTokenService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";
    private static final int MIN_SECRET_LENGTH = 32;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] secret;
    private final long expirationSeconds;

    public JwtTokenService(@Value("${app.security.jwt.secret}") String secret,
                           @Value("${app.security.jwt.expiration-seconds:3600}") long expirationSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret тохируулаагүй байна.");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT secret хамгийн багадаа " + MIN_SECRET_LENGTH + " тэмдэгт байх ёстой.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public String issueToken(ApiDtos.AuthUserDto user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.username());
        claims.put("uid", user.id());
        if (user.displayName() != null) {
            claims.put("displayName", user.displayName());
        }
        claims.put("role", user.role());
        return issueToken(claims);
    }

    public String issuePasswordResetToken(Long userId, String username, String role) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", username);
        claims.put("uid", userId);
        claims.put("role", role);
        claims.put("purpose", PURPOSE_PASSWORD_RESET);
        return issueToken(claims);
    }

    private String issueToken(Map<String, Object> claims) {
        try {
            Instant now = Instant.now();
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.putAll(claims);
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", now.plusSeconds(expirationSeconds).getEpochSecond());

            String encodedHeader = encode(objectMapper.writeValueAsBytes(header));
            String encodedPayload = encode(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            return signingInput + "." + sign(signingInput);
        } catch (Exception exception) {
            throw new IllegalStateException("JWT token үүсгэхэд алдаа гарлаа.", exception);
        }
    }

    public ApiDtos.AuthUserDto parseToken(String token) {
        Map<String, Object> payload = verifyAndDecode(token);
        Object purpose = payload.get("purpose");
        if (purpose != null && PURPOSE_PASSWORD_RESET.equalsIgnoreCase(String.valueOf(purpose))) {
            throw new BadCredentialsException("Энэ token нь зөвхөн нууц үг сэргээх зориулалттай тул нэвтрэхэд ашиглах боломжгүй.");
        }
        String username = stringClaim(payload.get("sub"));
        String displayName = stringClaim(payload.get("displayName"));
        return new ApiDtos.AuthUserDto(
                longValue(payload.get("uid")),
                username,
                displayName == null ? username : displayName,
                stringClaim(payload.get("role"))
        );
    }

    public ApiDtos.AuthUserDto parsePasswordResetToken(String token) {
        Map<String, Object> payload = verifyAndDecode(token);
        Object purpose = payload.get("purpose");
        if (purpose == null || !PURPOSE_PASSWORD_RESET.equalsIgnoreCase(String.valueOf(purpose))) {
            throw new BadCredentialsException("Нууц үг сэргээх token биш байна.");
        }
        return new ApiDtos.AuthUserDto(
                longValue(payload.get("uid")),
                stringClaim(payload.get("sub")),
                null,
                stringClaim(payload.get("role"))
        );
    }

    private Map<String, Object> verifyAndDecode(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw new BadCredentialsException("JWT token хоосон байна.");
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BadCredentialsException("JWT формат буруу байна.");
            }
            String signingInput = parts[0] + "." + parts[1];
            String expectedSignature = sign(signingInput);
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new BadCredentialsException("JWT signature буруу байна.");
            }
            Map<String, Object> payload = objectMapper.readValue(URL_DECODER.decode(parts[1]), MAP_TYPE);
            Object expirationClaim = payload.get("exp");
            if (expirationClaim == null) {
                throw new BadCredentialsException("JWT exp claim олдсонгүй.");
            }
            if (Instant.now().isAfter(Instant.ofEpochSecond(longValue(expirationClaim)))) {
                throw new BadCredentialsException("JWT token хугацаа дууссан байна.");
            }
            return payload;
        } catch (BadCredentialsException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BadCredentialsException("JWT token уншихад алдаа гарлаа.", exception);
        }
    }

    private String stringClaim(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public String resolveBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        return token.isBlank() ? null : token;
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return encode(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
