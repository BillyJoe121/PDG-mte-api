package co.edu.icesi.pdg.mte.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mte.auth")
public record AuthProperties(
        String mode,
        String externalBaseUrl,
        String introspectionPath
) {
    public boolean externalMode() {
        return "external".equalsIgnoreCase(mode);
    }
}
