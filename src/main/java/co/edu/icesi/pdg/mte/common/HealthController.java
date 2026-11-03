package co.edu.icesi.pdg.mte.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private static final String SERVICE = "pdg-mte-api";
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    ResponseEntity<Map<String, Object>> health() {
        boolean databaseUp = isDatabaseUp();
        String state = databaseUp ? "UP" : "DOWN";
        return ResponseEntity.status(databaseUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", state,
                        "database", state,
                        "service", SERVICE,
                        "timestamp", Instant.now()
                ));
    }

    private boolean isDatabaseUp() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return true;
            }
            log.warn("Health check: database connection is not valid");
            return false;
        } catch (Exception exception) {
            // Only the exception type is logged: messages may carry hosts or credentials.
            log.warn("Health check: database unavailable ({})", exception.getClass().getName());
            return false;
        }
    }
}
