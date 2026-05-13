package co.edu.icesi.pdg.mte.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "mte.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
    public List<String> origins() {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return List.of("http://localhost:3000", "http://localhost:5173", "http://localhost:4200");
        }
        return allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .toList();
    }
}
