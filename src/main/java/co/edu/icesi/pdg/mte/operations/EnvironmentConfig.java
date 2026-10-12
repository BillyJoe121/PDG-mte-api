package co.edu.icesi.pdg.mte.operations;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "environment_config")
public class EnvironmentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "environment_name", nullable = false, unique = true, length = 64)
    private String environmentName;

    @Column(name = "database_identifier", nullable = false, length = 128)
    private String databaseIdentifier;

    @Column(name = "sanitized_endpoint", nullable = false)
    private String sanitizedEndpoint;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public EnvironmentConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEnvironmentName() { return environmentName; }
    public void setEnvironmentName(String environmentName) { this.environmentName = environmentName; }

    public String getDatabaseIdentifier() { return databaseIdentifier; }
    public void setDatabaseIdentifier(String databaseIdentifier) { this.databaseIdentifier = databaseIdentifier; }

    public String getSanitizedEndpoint() { return sanitizedEndpoint; }
    public void setSanitizedEndpoint(String sanitizedEndpoint) { this.sanitizedEndpoint = sanitizedEndpoint; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
