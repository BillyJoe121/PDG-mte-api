package co.edu.icesi.pdg.mte.security;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsoTokenClientTest {
    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void exchangesCodeUsingFixedRedirectPkceAndConfidentialSecret() {
        respond(200, "{\"access_token\":\"provider-token\",\"token_type\":\"Bearer\",\"expires_in\":7200}");

        var token = client("server-only-secret").exchange("authorization-code", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~");

        assertThat(token.accessToken()).isEqualTo("provider-token");
        assertThat(token.expiresIn()).isEqualTo(7200);
        assertThat(requestBody.get())
                .contains("grant_type=authorization_code")
                .contains("code=authorization-code")
                .contains("client_id=mte-client")
                .contains("redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fauth%2Fcallback")
                .contains("code_verifier=")
                .contains("client_secret=server-only-secret");
    }

    @Test
    void rejectsNonBearerProviderResponse() {
        respond(200, "{\"access_token\":\"provider-token\",\"token_type\":\"MAC\"}");

        assertThatThrownBy(() -> client(null).exchange("authorization-code", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("bearer");
    }

    private SsoTokenClient client(String secret) {
        int port = server.getAddress().getPort();
        var properties = new SsoProperties(
                true,
                "ICESI",
                "http://localhost:" + port + "/authorize",
                "http://localhost:" + port + "/token",
                "mte-client",
                secret,
                "http://localhost:5173/auth/callback",
                List.of("openid", "profile", "email")
        );
        return new SsoTokenClient(WebClient.builder(), properties);
    }

    private void respond(int status, String body) {
        server.createContext("/token", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, status, body);
        });
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
