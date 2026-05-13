package co.edu.icesi.pdg.mte.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mte.integration.trayectoria")
public class IntegrationProperties {
    private String mode = "mock";
    private String externalBaseUrl = "http://localhost:8080/api/v1";
    private String projectsPath = "/proyectos";
    private String sourceName = "TRAYECTORIA_DOCENTE";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getExternalBaseUrl() {
        return externalBaseUrl;
    }

    public void setExternalBaseUrl(String externalBaseUrl) {
        this.externalBaseUrl = externalBaseUrl;
    }

    public String getProjectsPath() {
        return projectsPath;
    }

    public void setProjectsPath(String projectsPath) {
        this.projectsPath = projectsPath;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }
}
