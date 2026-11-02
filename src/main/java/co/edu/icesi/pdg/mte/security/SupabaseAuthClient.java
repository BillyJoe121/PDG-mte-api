package co.edu.icesi.pdg.mte.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-side calls to Supabase Auth. Passwords and tokens are never logged or echoed in errors.
 */
@Component
public class SupabaseAuthClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient;
    private final SupabaseProperties properties;

    public SupabaseAuthClient(WebClient.Builder builder, SupabaseProperties properties) {
        this.webClient = builder.build();
        this.properties = properties;
    }

    public Session signInWithPassword(String email, String password) {
        JsonNode body = call(post("/token?grant_type=password", serverHeaders(), Map.of("email", email, "password", password)));
        return session(body);
    }

    public void signOut(String accessToken) {
        call(post("/logout?scope=local", headers -> {
            headers.set("apikey", requireConfigured().serverKey());
            headers.setBearerAuth(accessToken);
        }, Map.of()));
    }

    public void updatePassword(UUID userId, String password) {
        call(webClient.put()
                .uri(properties.authBaseUrl() + "/admin/users/" + userId)
                .headers(serverHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("password", password)));
    }

    public GeneratedLink generateLink(String type, String email) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", type);
        request.put("email", email);
        JsonNode body = call(post("/admin/generate_link", serverHeaders(), request));
        String hashedToken = text(body.path("hashed_token"));
        if (hashedToken == null) {
            hashedToken = text(body.path("properties").path("hashed_token"));
        }
        String verificationType = text(body.path("verification_type"));
        if (verificationType == null) {
            verificationType = text(body.path("properties").path("verification_type"));
        }
        UUID userId = uuid(body.path("id").isMissingNode() ? body.path("user").path("id") : body.path("id"));
        if (hashedToken == null || userId == null) {
            throw new UnavailableException("Supabase did not return a usable access link.");
        }
        return new GeneratedLink(userId, hashedToken, verificationType == null ? type : verificationType);
    }

    public Session verifyTokenHash(String type, String tokenHash) {
        JsonNode body = call(post("/verify", serverHeaders(), Map.of("type", type, "token_hash", tokenHash)));
        return session(body);
    }

    private WebClient.RequestHeadersSpec<?> post(String path, Consumer<HttpHeaders> headers, Map<String, ?> body) {
        return webClient.post()
                .uri(requireConfigured().authBaseUrl() + path)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }

    private Consumer<HttpHeaders> serverHeaders() {
        String key = requireConfigured().serverKey();
        return headers -> {
            headers.set("apikey", key);
            // Legacy JWT keys also travel as bearer; the newer "sb_" secret keys only go in apikey.
            if (!key.startsWith("sb_")) {
                headers.setBearerAuth(key);
            }
        };
    }

    private SupabaseProperties requireConfigured() {
        if (!properties.configured()) {
            throw new UnavailableException("Supabase Auth is not configured.");
        }
        return properties;
    }

    private JsonNode call(WebClient.RequestHeadersSpec<?> request) {
        try {
            JsonNode body = request.exchangeToMono(this::read).block(TIMEOUT);
            return body == null ? MissingNode.getInstance() : body;
        } catch (SupabaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new UnavailableException("Supabase Auth could not be reached.");
        }
    }

    private Mono<JsonNode> read(ClientResponse response) {
        HttpStatusCode status = response.statusCode();
        if (status.is2xxSuccessful()) {
            return response.bodyToMono(JsonNode.class);
        }
        return response.bodyToMono(JsonNode.class)
                .defaultIfEmpty(MissingNode.getInstance())
                .onErrorReturn(MissingNode.getInstance())
                .flatMap(body -> Mono.error(failure(status, body)));
    }

    static SupabaseException failure(HttpStatusCode status, JsonNode body) {
        String code = firstText(body, "error_code", "error");
        if (status.is5xxServerError() || status.value() == 429) {
            return new UnavailableException("Supabase Auth is not available (" + status.value() + ").");
        }
        if ("email_exists".equals(code) || "user_already_exists".equals(code)) {
            return new EmailExistsException();
        }
        if ("weak_password".equals(code)) {
            return new RejectedException("weak_password");
        }
        if (status.value() == 400 || status.value() == 401 || status.value() == 403 || status.value() == 404) {
            return new InvalidCredentialsException(code == null ? String.valueOf(status.value()) : code);
        }
        return new RejectedException(code == null ? String.valueOf(status.value()) : code);
    }

    private static Session session(JsonNode body) {
        String accessToken = text(body.path("access_token"));
        UUID userId = uuid(body.path("user").path("id"));
        if (accessToken == null || userId == null) {
            throw new UnavailableException("Supabase did not return a usable session.");
        }
        long expiresIn = body.path("expires_in").asLong(3600);
        long expiresAt = body.path("expires_at").asLong(0);
        return new Session(accessToken, expiresIn, expiresAt, userId, text(body.path("user").path("email")));
    }

    private static String firstText(JsonNode body, String... fields) {
        for (String field : fields) {
            String value = text(body.path(field));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    private static UUID uuid(JsonNode node) {
        String value = text(node);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record Session(String accessToken, long expiresIn, long expiresAt, UUID userId, String email) {
    }

    public record GeneratedLink(UUID userId, String hashedToken, String verificationType) {
    }

    public static class SupabaseException extends RuntimeException {
        SupabaseException(String message) {
            super(message);
        }
    }

    /** Wrong credentials or an invalid, used or expired link. */
    public static class InvalidCredentialsException extends SupabaseException {
        InvalidCredentialsException(String code) {
            super(code);
        }
    }

    public static class EmailExistsException extends SupabaseException {
        EmailExistsException() {
            super("email_exists");
        }
    }

    public static class RejectedException extends SupabaseException {
        RejectedException(String code) {
            super(code);
        }
    }

    public static class UnavailableException extends SupabaseException {
        UnavailableException(String message) {
            super(message);
        }
    }
}
