package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "mte.auth.mode=external",
        "mte.auth.sso.enabled=true",
        "mte.auth.sso.provider-name=ICESI Entra ID",
        "mte.auth.sso.authorization-uri=https://identity.example/authorize",
        "mte.auth.sso.token-uri=https://identity.example/token",
        "mte.auth.sso.client-id=mte-client",
        "mte.auth.sso.client-secret=never-expose-this",
        "mte.auth.sso.redirect-uri=http://localhost:5173/auth/callback",
        "mte.auth.sso.scopes=openid,profile,email"
})
@AutoConfigureMockMvc
class SsoFlowE2ETest {
    private static final String VERIFIER = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";

    @Autowired MockMvc mockMvc;
    @MockitoBean SsoTokenClient tokenClient;

    @Test
    void publishesOnlySafeConfigurationWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/sso/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.providerName").value("ICESI Entra ID"))
                .andExpect(jsonPath("$.authorizationUri").value("https://identity.example/authorize"))
                .andExpect(jsonPath("$.clientId").value("mte-client"))
                .andExpect(jsonPath("$.redirectUri").value("http://localhost:5173/auth/callback"))
                .andExpect(jsonPath("$.scopes[0]").value("openid"))
                .andExpect(jsonPath("$.tokenUri").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    void exchangesAuthorizationCodeWithoutAnExistingBearer() throws Exception {
        when(tokenClient.exchange("single-use-code", VERIFIER))
                .thenReturn(new SecurityDtos.SsoTokenResponse("access-token", "Bearer", 3600));

        mockMvc.perform(post("/api/v1/auth/sso/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"single-use-code","codeVerifier":"%s"}
                                """.formatted(VERIFIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        verify(tokenClient).exchange("single-use-code", VERIFIER);
    }

    @Test
    void rejectsPkceVerifierOutsideTheStandardLength() throws Exception {
        mockMvc.perform(post("/api/v1/auth/sso/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"single-use-code\",\"codeVerifier\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }
}
