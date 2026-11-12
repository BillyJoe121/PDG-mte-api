package co.edu.icesi.pdg.mte.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesUnexpectedExceptionsWithAndWithoutMessages() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");

        var withMessage = handler.handleUnexpected(new IllegalStateException("tabla interna sin columna"), request);
        var withoutMessage = handler.handleUnexpected(new RuntimeException(), request);

        assertThat(withMessage.getStatusCode().value()).isEqualTo(500);
        assertThat(withMessage.getBody().details()).noneMatch(detail -> detail.contains("tabla interna"));
        assertThat(withMessage.getBody().details()).isEqualTo(withoutMessage.getBody().details());
        assertThat(withoutMessage.getBody().details()).noneMatch(detail -> detail.contains("RuntimeException"));
    }

    @Test
    void handlesMissingResourcesAsNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/key-results/1/progress");

        var response = handler.handleNoResource(new NoResourceFoundException(HttpMethod.PATCH, "/api/v1/key-results/1/progress"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Recurso no encontrado.");
        assertThat(response.getBody().details()).noneMatch(detail -> detail.contains("/api/v1"));
    }

    @Test
    void handlesUnsupportedMethodsWithoutEchoingTheException() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/health");

        var response = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PUT"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().details()).containsExactly("Metodo PUT no soportado en esta ruta.");
    }

    @Test
    void handlesAuthorizationDeniedAsForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/measurement-units");

        var response = handler.handleAuthorizationDenied(new AuthorizationDeniedException("Access Denied"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().message()).isEqualTo("Acceso denegado.");
    }

    @Test
    void handlesUnreadableRequestBodyAsBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/projects/1/status");

        var response = handler.handleMessageNotReadable(new HttpMessageNotReadableException("enum invalido"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("La solicitud tiene un cuerpo invalido.");
        assertThat(response.getBody().details()).noneMatch(detail -> detail.contains("enum invalido"));
    }
}
