package co.edu.icesi.pdg.mte.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates the Supabase access token on each request and loads the MTE person and role behind
 * it. Requests without a token continue unauthenticated and are rejected by the authorization
 * rules unless the endpoint is public.
 */
@Component
public class MteAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final AuthService authService;
    private final AccessControlService accessControlService;
    private final RestAuthenticationEntryPoint entryPoint;

    public MteAuthenticationFilter(
            JwtDecoder jwtDecoder,
            AuthService authService,
            AccessControlService accessControlService,
            RestAuthenticationEntryPoint entryPoint
    ) {
        this.jwtDecoder = jwtDecoder;
        this.authService = authService;
        this.accessControlService = accessControlService;
        this.entryPoint = entryPoint;
    }

    /** Returns the token of an {@code Authorization: Bearer} header, or null when there is none. */
    public static String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.equals("/api/v1/health")
                || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/set-password")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/h2-console");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ExternalUserContext userContext;
        try {
            Jwt jwt = jwtDecoder.decode(token);
            userContext = authService.resolve(jwt);
        } catch (JwtException exception) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException("Token inválido.", exception));
            return;
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, exception);
            return;
        }

        List<SimpleGrantedAuthority> authorities = accessControlService.normalizeToList(userContext.roles())
                .stream()
                .map(role -> "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userContext, null, authorities));
        filterChain.doFilter(request, response);
    }
}
