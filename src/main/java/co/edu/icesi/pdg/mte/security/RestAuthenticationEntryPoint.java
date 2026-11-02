package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Writes 401 and 403 responses with the same {@link ApiError} body the controllers use.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {
    static final String SIGN_IN_REQUIRED = "Inicia sesión para continuar.";

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String detail = exception instanceof DisabledException
                ? exception.getMessage()
                : "Sesión ausente, inválida o vencida.";
        write(response, request, HttpStatus.UNAUTHORIZED, SIGN_IN_REQUIRED, detail);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, "Acceso denegado.", "Access Denied");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String message, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiError body = new ApiError(status.value(), message, List.of(detail), Instant.now(), request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
