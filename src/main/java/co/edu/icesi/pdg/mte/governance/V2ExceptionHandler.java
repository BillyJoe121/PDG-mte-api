package co.edu.icesi.pdg.mte.governance;

import co.edu.icesi.pdg.mte.common.BusinessException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@Order(-1)
@RestControllerAdvice(basePackages = "co.edu.icesi.pdg.mte.governance")
public class V2ExceptionHandler {
    private ResponseEntity<Map<String, String>> response(int status, String code) {
        String traceId=UUID.randomUUID().toString();
        return ResponseEntity.status(status).header("X-Trace-Id",traceId).body(Map.of("error", code, "traceId",traceId));
    }
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> business(BusinessException e) { return response(e.status().value(), e.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integrity() { return response(409, "DATA_CONFLICT"); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denied() { return response(403, "FORBIDDEN"); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidBody() { return response(422, "INVALID_REQUEST"); }
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> invalidParameter() { return response(422,"INVALID_REQUEST"); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected() { return response(500,"INTERNAL_ERROR"); }
}
