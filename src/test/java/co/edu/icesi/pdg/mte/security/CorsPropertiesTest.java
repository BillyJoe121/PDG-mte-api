package co.edu.icesi.pdg.mte.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPropertiesTest {

    @Test
    void defaultsToLocalFrontendOriginsWhenNoOriginsAreConfigured() {
        assertThat(new CorsProperties(null).origins())
                .containsExactly("http://localhost:3000", "http://localhost:5173", "http://localhost:4200");
        assertThat(new CorsProperties(List.of()).origins())
                .containsExactly("http://localhost:3000", "http://localhost:5173", "http://localhost:4200");
    }

    @Test
    void trimsConfiguredOriginsAndDropsBlankValues() {
        assertThat(new CorsProperties(List.of(" https://app.example.edu ", "", "   ", "http://localhost:5173")).origins())
                .containsExactly("https://app.example.edu", "http://localhost:5173");
    }
}
