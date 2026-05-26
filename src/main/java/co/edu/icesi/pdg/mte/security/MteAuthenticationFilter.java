package co.edu.icesi.pdg.mte.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class MteAuthenticationFilter extends OncePerRequestFilter {

    private final AuthProperties properties;
    private final ExternalAuthClient externalAuthClient;
    private final AccessControlService accessControlService;

    public MteAuthenticationFilter(
            AuthProperties properties,
            ExternalAuthClient externalAuthClient,
            AccessControlService accessControlService
    ) {
        this.properties = properties;
        this.externalAuthClient = externalAuthClient;
        this.accessControlService = accessControlService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.equals("/api/v1/health")
                || path.startsWith("/api/v1/health/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/h2-console");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ExternalUserContext userContext = properties.externalMode()
                ? validateExternal(request)
                : mockContext(request);

        List<SimpleGrantedAuthority> authorities = accessControlService.normalizeToList(userContext.roles())
                .stream()
                .map(role -> "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toList();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userContext,
                null,
                authorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private ExternalUserContext validateExternal(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new org.springframework.security.authentication.BadCredentialsException("Bearer token requerido.");
        }
        return externalAuthClient.introspect(authorization);
    }

    private ExternalUserContext mockContext(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer mock-token-")) {
            return ExternalUserContext.mock();
        }
        String tokenRole = authorization.substring("Bearer mock-token-".length());
        ExternalUserContext demoUser = demoUserContext(tokenRole);
        if (demoUser != null) {
            return demoUser;
        }
        List<String> roles = accessControlService.normalizeToList(List.of(tokenRole));
        return new ExternalUserContext(
                1L,
                "demo." + tokenRole,
                "demo." + tokenRole + "@icesi.edu.co",
                roles.isEmpty() ? ExternalUserContext.mock().roles() : roles,
                List.of("MTE_READ", "MTE_WRITE"),
                1L,
                "Usuario Demo",
                "Departamento de Computaci\u00f3n y Sistemas inteligentes."
        );
    }

    private ExternalUserContext demoUserContext(String token) {
        return switch (token.toLowerCase(java.util.Locale.ROOT)) {
            case "ha" -> demoUser(
                    9001L,
                    "ha",
                    "hugo.arboleda@icesi.edu.co",
                    List.of(AccessControlService.DIRECTOR_ESCUELA),
                    "Hugo Arboleda",
                    "Direcci\u00f3n TDI"
            );
            case "rs" -> demoUser(
                    9002L,
                    "rs",
                    "rocio.segovia@icesi.edu.co",
                    List.of(AccessControlService.JEFE_DPTO),
                    "Roc\u00edo Segovia",
                    "DCSI"
            );
            case "lb" -> demoUser(
                    9003L,
                    "lb",
                    "leonardo.bustamante@icesi.edu.co",
                    List.of(AccessControlService.PROFESOR),
                    "Leonardo Bustamante",
                    "DCSI"
            );
            case "ad" -> demoUser(
                    9004L,
                    "ad",
                    "sistemas.mte@icesi.edu.co",
                    List.of(AccessControlService.ADMIN),
                    "Sistemas MTE",
                    "TI Institucional"
            );
            default -> null;
        };
    }

    private ExternalUserContext demoUser(
            Long id,
            String username,
            String email,
            List<String> roles,
            String professorName,
            String departmentName
    ) {
        return new ExternalUserContext(
                id,
                username,
                email,
                roles,
                List.of("MTE_READ", "MTE_WRITE"),
                id,
                professorName,
                departmentName
        );
    }
}
