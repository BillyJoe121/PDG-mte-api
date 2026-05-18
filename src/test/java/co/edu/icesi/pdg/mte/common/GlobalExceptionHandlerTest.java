package co.edu.icesi.pdg.mte.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesUnexpectedExceptionsWithAndWithoutMessages() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");

        var withMessage = handler.handleUnexpected(new IllegalStateException("boom"), request);
        var withoutMessage = handler.handleUnexpected(new RuntimeException(), request);

        assertThat(withMessage.getStatusCode().value()).isEqualTo(500);
        assertThat(withMessage.getBody().details()).containsExactly("boom");
        assertThat(withoutMessage.getBody().details()).containsExactly("RuntimeException");
    }

    @Test
    void handlesMissingResourcesAsNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/key-results/1/current-value");

        var response = handler.handleNoResource(new NoResourceFoundException(HttpMethod.PATCH, "/api/v1/key-results/1/current-value"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Recurso no encontrado.");
    }

    @Test
    void handlesAuthorizationDeniedAsForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/measurement-units");

        var response = handler.handleAuthorizationDenied(new AuthorizationDeniedException("Access Denied"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().message()).isEqualTo("Acceso denegado.");
    }
}
