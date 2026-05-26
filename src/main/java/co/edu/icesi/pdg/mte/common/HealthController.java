package co.edu.icesi.pdg.mte.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "pdg-mte-api",
                "timestamp", Instant.now()
        );
    }

    @GetMapping("/db")
    Map<String, Object> database() {
        long startedAt = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            return Map.of(
                    "status", valid ? "UP" : "DOWN",
                    "database", connection.getMetaData().getDatabaseProductName(),
                    "valid", valid,
                    "durationMs", elapsedMs(startedAt),
                    "timestamp", Instant.now()
            );
        } catch (SQLException exception) {
            return Map.of(
                    "status", "DOWN",
                    "error", exception.getMessage(),
                    "durationMs", elapsedMs(startedAt),
                    "timestamp", Instant.now()
            );
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
