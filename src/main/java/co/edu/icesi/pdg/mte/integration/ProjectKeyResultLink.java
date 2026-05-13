package co.edu.icesi.pdg.mte.integration;

import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "project_key_result_link")
public class ProjectKeyResultLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private KeyResult keyResult;

    @ManyToOne(fetch = FetchType.LAZY)
    private Project project;

    private Long externalProjectId;

    @Column(nullable = false)
    private String externalProjectSource = "TRAYECTORIA_DOCENTE";

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal contributionWeight;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public KeyResult getKeyResult() {
        return keyResult;
    }

    public void setKeyResult(KeyResult keyResult) {
        this.keyResult = keyResult;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
        if (project != null) {
            this.externalProjectId = project.getExternalProjectId();
            this.externalProjectSource = project.getExternalSource() == null ? "LOCAL_MSP" : project.getExternalSource();
        }
    }

    public Long getExternalProjectId() {
        return externalProjectId;
    }

    public void setExternalProjectId(Long externalProjectId) {
        this.externalProjectId = externalProjectId;
    }

    public String getExternalProjectSource() {
        return externalProjectSource;
    }

    public void setExternalProjectSource(String externalProjectSource) {
        this.externalProjectSource = externalProjectSource;
    }

    public BigDecimal getContributionWeight() {
        return contributionWeight;
    }

    public void setContributionWeight(BigDecimal contributionWeight) {
        this.contributionWeight = contributionWeight;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
