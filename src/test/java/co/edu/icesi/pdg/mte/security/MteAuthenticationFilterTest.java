package co.edu.icesi.pdg.mte.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MteAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotFilterPublicRoutesAndOptionsRequests() {
        MteAuthenticationFilter filter = new MteAuthenticationFilter(
                new AuthProperties("mock", "http://localhost", "/auth/me"),
                mock(ExternalAuthClient.class)
        );

        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/health"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/swagger-ui/index.html"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/v3/api-docs"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/h2-console"))).isTrue();
        assertThat(filter.shouldNotFilter(request("OPTIONS", "/api/v1/objectives"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/objectives"))).isFalse();
    }

    @Test
    void mockModeAuthenticatesWithDemoContext() throws Exception {
        MteAuthenticationFilter filter = new MteAuthenticationFilter(
                new AuthProperties("mock", "http://localhost", "/auth/me"),
                mock(ExternalAuthClient.class)
        );
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request("GET", "/api/v1/objectives"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void externalModeAuthenticatesWithBearerToken() throws Exception {
        ExternalAuthClient client = mock(ExternalAuthClient.class);
        when(client.introspect("Bearer token")).thenReturn(new ExternalUserContext(
                1L,
                "admin",
                "admin@icesi.edu.co",
                java.util.List.of("ROLE_ADMIN"),
                java.util.List.of(),
                null,
                null,
                null
        ));
        MteAuthenticationFilter filter = new MteAuthenticationFilter(
                new AuthProperties("external", "http://localhost", "/auth/me"),
                client
        );
        MockHttpServletRequest request = request("GET", "/api/v1/objectives");
        request.addHeader("Authorization", "Bearer token");

        filter.doFilterInternal(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        verify(client).introspect("Bearer token");
    }

    @Test
    void externalModeRejectsMissingBearerToken() {
        MteAuthenticationFilter filter = new MteAuthenticationFilter(
                new AuthProperties("external", "http://localhost", "/auth/me"),
                mock(ExternalAuthClient.class)
        );

        assertThatThrownBy(() -> filter.doFilterInternal(
                request("GET", "/api/v1/objectives"),
                new MockHttpServletResponse(),
                mock(FilterChain.class)
        )).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void externalModeRejectsMalformedAuthorizationHeader() {
        MteAuthenticationFilter filter = new MteAuthenticationFilter(
                new AuthProperties("external", "http://localhost", "/auth/me"),
                mock(ExternalAuthClient.class)
        );
        MockHttpServletRequest request = request("GET", "/api/v1/objectives");
        request.addHeader("Authorization", "Token nope");

        assertThatThrownBy(() -> filter.doFilterInternal(
                request,
                new MockHttpServletResponse(),
                mock(FilterChain.class)
        )).isInstanceOf(BadCredentialsException.class);
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }
}
