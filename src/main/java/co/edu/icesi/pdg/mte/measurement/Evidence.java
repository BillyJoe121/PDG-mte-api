package co.edu.icesi.pdg.mte.measurement;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "evidence")
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measurement_id")
    private Long measurementId;

    @Column(name = "initiative_id")
    private Long initiativeId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "evidence_type", nullable = false, length = 50)
    private String evidenceType;

    @Column(name = "external_ref", length = 1000)
    private String externalRef;

    @Column(name = "file_hash_sha256", length = 64)
    private String fileHashSha256;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Evidence() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMeasurementId() { return measurementId; }
    public void setMeasurementId(Long measurementId) { this.measurementId = measurementId; }
    public Long getInitiativeId() { return initiativeId; }
    public void setInitiativeId(Long initiativeId) { this.initiativeId = initiativeId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public String getFileHashSha256() { return fileHashSha256; }
    public void setFileHashSha256(String fileHashSha256) { this.fileHashSha256 = fileHashSha256; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
