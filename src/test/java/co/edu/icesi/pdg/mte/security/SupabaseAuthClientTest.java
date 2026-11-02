package co.edu.icesi.pdg.mte.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupabaseAuthClientTest {
    private static final String USER_ID = "8f9c3a4e-2b1d-4c5e-9f00-112233445566";

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void signInSendsPasswordGrantWithServerKeyAndReadsTheSession() throws Exception {
        SupabaseAuthClient client = client(200, """
                {"access_token":"jwt-value","expires_in":3600,"expires_at":1900000000,
                 "user":{"id":"%s","email":"persona@icesi.edu.co"}}
                """.formatted(USER_ID), "sb_secret_test");

        SupabaseAuthClient.Session session = client.signInWithPassword("persona@icesi.edu.co", "clave-segura-1");

        assertThat(path.get()).isEqualTo("/auth/v1/token?grant_type=password");
        assertThat(apiKey.get()).isEqualTo("sb_secret_test");
        assertThat(authorization.get()).isNull();
        assertThat(body.get()).contains("\"email\":\"persona@icesi.edu.co\"").contains("clave-segura-1");
        assertThat(session.accessToken()).isEqualTo("jwt-value");
        assertThat(session.userId()).isEqualTo(UUID.fromString(USER_ID));
        assertThat(session.expiresAt()).isEqualTo(1900000000L);
    }

    @Test
    void legacyServerKeyAlsoTravelsAsBearer() throws Exception {
        SupabaseAuthClient client = client(200, "{}", "legacy-service-role-jwt");

        client.updatePassword(UUID.fromString(USER_ID), "clave-segura-2");

        assertThat(path.get()).isEqualTo("/auth/v1/admin/users/" + USER_ID);
        assertThat(authorization.get()).isEqualTo("Bearer legacy-service-role-jwt");
    }

    @Test
    void wrongCredentialsAreReportedWithoutEchoingThePassword() throws Exception {
        SupabaseAuthClient client = client(400, """
                {"error_code":"invalid_credentials","msg":"Invalid login credentials"}
                """, "sb_secret_test");

        assertThatThrownBy(() -> client.signInWithPassword("persona@icesi.edu.co", "clave-equivocada"))
                .isInstanceOf(SupabaseAuthClient.InvalidCredentialsException.class)
                .hasMessageNotContaining("clave-equivocada");
    }

    @Test
    void serverErrorsAndRateLimitsMeanUnavailable() throws Exception {
        SupabaseAuthClient failing = client(500, "{}", "sb_secret_test");
        assertThatThrownBy(() -> failing.signInWithPassword("persona@icesi.edu.co", "clave-segura-1"))
                .isInstanceOf(SupabaseAuthClient.UnavailableException.class);
        server.stop(0);

        SupabaseAuthClient limited = client(429, "{}", "sb_secret_test");
        assertThatThrownBy(() -> limited.signInWithPassword("persona@icesi.edu.co", "clave-segura-1"))
                .isInstanceOf(SupabaseAuthClient.UnavailableException.class);
    }

    @Test
    void weakPasswordAndExistingEmailHaveTheirOwnErrors() throws Exception {
        SupabaseAuthClient weak = client(422, "{\"error_code\":\"weak_password\"}", "sb_secret_test");
        assertThatThrownBy(() -> weak.updatePassword(UUID.fromString(USER_ID), "corta"))
                .isInstanceOf(SupabaseAuthClient.RejectedException.class);
        server.stop(0);

        SupabaseAuthClient exists = client(422, "{\"error_code\":\"email_exists\"}", "sb_secret_test");
        assertThatThrownBy(() -> exists.generateLink("invite", "persona@icesi.edu.co"))
                .isInstanceOf(SupabaseAuthClient.EmailExistsException.class);
    }

    @Test
    void generateLinkReadsTheHashedTokenAndUser() throws Exception {
        SupabaseAuthClient client = client(200, """
                {"id":"%s","email":"persona@icesi.edu.co","action_link":"https://ignored",
                 "hashed_token":"abc123","verification_type":"invite"}
                """.formatted(USER_ID), "sb_secret_test");

        SupabaseAuthClient.GeneratedLink link = client.generateLink("invite", "persona@icesi.edu.co");

        assertThat(path.get()).isEqualTo("/auth/v1/admin/generate_link");
        assertThat(body.get()).contains("\"type\":\"invite\"");
        assertThat(link.userId()).isEqualTo(UUID.fromString(USER_ID));
        assertThat(link.hashedToken()).isEqualTo("abc123");
        assertThat(link.verificationType()).isEqualTo("invite");
    }

    @Test
    void verifyTokenHashReturnsASession() throws Exception {
        SupabaseAuthClient client = client(200, """
                {"access_token":"jwt-link","user":{"id":"%s","email":"persona@icesi.edu.co"}}
                """.formatted(USER_ID), "sb_secret_test");

        SupabaseAuthClient.Session session = client.verifyTokenHash("recovery", "hash-1");

        assertThat(path.get()).isEqualTo("/auth/v1/verify");
        assertThat(body.get()).contains("\"token_hash\":\"hash-1\"").contains("\"type\":\"recovery\"");
        assertThat(session.email()).isEqualTo("persona@icesi.edu.co");
    }

    @Test
    void missingConfigurationFailsWithoutCallingOut() {
        SupabaseAuthClient client = new SupabaseAuthClient(WebClient.builder(), new SupabaseProperties("", "", ""));

        assertThatThrownBy(() -> client.signInWithPassword("persona@icesi.edu.co", "clave-segura-1"))
                .isInstanceOf(SupabaseAuthClient.UnavailableException.class);
    }

    @Test
    void unreachableServiceMeansUnavailable() {
        SupabaseAuthClient client = new SupabaseAuthClient(WebClient.builder(),
                new SupabaseProperties("http://127.0.0.1:1", "sb_secret_test", ""));

        assertThatThrownBy(() -> client.signInWithPassword("persona@icesi.edu.co", "clave-segura-1"))
                .isInstanceOf(SupabaseAuthClient.UnavailableException.class);
    }

    private SupabaseAuthClient client(int status, String response, String serverKey) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().toString());
            apiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        return new SupabaseAuthClient(WebClient.builder(), new SupabaseProperties(url, serverKey, ""));
    }
}
