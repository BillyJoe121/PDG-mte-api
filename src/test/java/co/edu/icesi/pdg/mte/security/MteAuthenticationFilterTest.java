package co.edu.icesi.pdg.mte.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MteAuthenticationFilterTest {
    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final AuthService authService = mock(AuthService.class);
    private final MteAuthenticationFilter filter = new MteAuthenticationFilter(
            jwtDecoder,
            authService,
            new AccessControlService(),
            new RestAuthenticationEntryPoint(new ObjectMapper().registerModule(new JavaTimeModule()))
    );

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotFilterPublicRoutesAndOptionsRequests() {
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/health"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/auth/login"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/auth/set-password"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/swagger-ui/index.html"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/v3/api-docs"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/h2-console"))).isTrue();
        assertThat(filter.shouldNotFilter(request("OPTIONS", "/api/v1/key-results"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/key-results"))).isFalse();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/auth/password"))).isFalse();
    }

    @Test
    void readsBearerTokensCaseInsensitively() {
        assertThat(MteAuthenticationFilter.bearerToken("Bearer abc")).isEqualTo("abc");
        assertThat(MteAuthenticationFilter.bearerToken("bearer abc ")).isEqualTo("abc");
        assertThat(MteAuthenticationFilter.bearerToken("Basic abc")).isNull();
        assertThat(MteAuthenticationFilter.bearerToken("Bearer   ")).isNull();
        assertThat(MteAuthenticationFilter.bearerToken(null)).isNull();
    }

    @Test
    void requestWithoutTokenContinuesUnauthenticated() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request("GET", "/api/v1/key-results"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
        verifyNoInteractions(jwtDecoder, authService);
    }

    @Test
    void validTokenAuthenticatesWithTheRoleStoredForThePerson() throws Exception {
        Jwt jwt = jwt();
        when(jwtDecoder.decode("good")).thenReturn(jwt);
        when(authService.resolve(jwt)).thenReturn(user("MANAGER"));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(withToken("good"), new MockHttpServletResponse(), chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isInstanceOf(ExternalUserContext.class);
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MANAGER");
        verify(chain).doFilter(any(), any());
    }

    @Test
    void invalidTokenIsRejectedWithUnauthorizedJson() throws Exception {
        when(jwtDecoder.decode("bad")).thenThrow(new BadJwtException("expired"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(withToken("bad"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Inicia sesión para continuar.")
                .doesNotContain("expired");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(chain);
    }

    @Test
    void tokenOfUnknownOrInactivePersonIsRejected() throws Exception {
        Jwt jwt = jwt();
        when(jwtDecoder.decode("orphan")).thenReturn(jwt);
        when(authService.resolve(jwt)).thenThrow(new DisabledException(AuthService.NO_ACCESS));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(withToken("orphan"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(AuthService.NO_ACCESS);
        verifyNoInteractions(chain);
    }

    private static Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token").header("alg", "ES256").subject(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
    }

    private static ExternalUserContext user(String role) {
        return new ExternalUserContext(7L, "persona@icesi.edu.co", "persona@icesi.edu.co", List.of(role),
                List.of(), 7L, "Persona", "DCSI");
    }

    private static MockHttpServletRequest withToken(String token) {
        MockHttpServletRequest request = request("GET", "/api/v1/key-results");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }
}
