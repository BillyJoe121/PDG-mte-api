package co.edu.icesi.pdg.mte.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "pdg-mte-api",
                "timestamp", Instant.now()
        );
    }
}
