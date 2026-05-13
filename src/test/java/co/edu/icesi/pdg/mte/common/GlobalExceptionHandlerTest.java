package co.edu.icesi.pdg.mte.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

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
}
