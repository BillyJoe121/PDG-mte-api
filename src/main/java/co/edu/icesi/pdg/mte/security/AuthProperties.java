package co.edu.icesi.pdg.mte.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "mte.auth")
public record AuthProperties(
        String mode,
        String externalBaseUrl,
        String introspectionPath,
        @DefaultValue("2s") Duration introspectionTimeout
) {
    public boolean externalMode() {
        return "external".equalsIgnoreCase(mode);
    }
}
