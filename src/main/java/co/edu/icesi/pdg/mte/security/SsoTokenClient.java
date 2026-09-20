package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SsoTokenClient {
    private static final long DEFAULT_EXPIRES_IN = 3600;
    private static final long MAX_EXPIRES_IN = 86_400;

    private final WebClient webClient;
    private final SsoProperties properties;

    public SsoTokenClient(WebClient.Builder builder, SsoProperties properties) {
        this.webClient = builder.build();
        this.properties = properties;
    }

    public SecurityDtos.SsoTokenResponse exchange(String code, String codeVerifier) {
        if (!properties.configured()) {
            throw new IllegalStateException("El SSO institucional no está configurado.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code_verifier", codeVerifier);
        if (properties.hasClientSecret()) form.add("client_secret", properties.clientSecret());

        JsonNode response;
        try {
            response = webClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), clientResponse ->
                            clientResponse.releaseBody().thenReturn(new BadCredentialsException("El proveedor rechazó el código de autorización.")))
                    .onStatus(status -> status.is5xxServerError(), clientResponse ->
                            clientResponse.releaseBody().thenReturn(new IllegalStateException("El proveedor de identidad no está disponible.")))
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (BadCredentialsException | IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("No fue posible contactar al proveedor de identidad.", exception);
        }

        String accessToken = response == null ? null : response.path("access_token").asText(null);
        String tokenType = response == null ? null : response.path("token_type").asText("Bearer");
        if (accessToken == null || accessToken.isBlank() || !"Bearer".equalsIgnoreCase(tokenType)) {
            throw new BadCredentialsException("El proveedor no entregó un token bearer válido.");
        }
        long expiresIn = response.path("expires_in").asLong(DEFAULT_EXPIRES_IN);
        expiresIn = Math.max(1, Math.min(expiresIn, MAX_EXPIRES_IN));
        return new SecurityDtos.SsoTokenResponse(accessToken, "Bearer", expiresIn);
    }
}
