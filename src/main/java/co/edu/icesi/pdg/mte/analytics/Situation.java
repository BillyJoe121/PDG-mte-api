package co.edu.icesi.pdg.mte.analytics;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "situation")
public class Situation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "origin_type", nullable = false, length = 50)
    private String originType;

    @Column(name = "origin_id")
    private Long originId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "cycle_id")
    private Long cycleId;

    @Column(nullable = false, length = 20)
    private String severity = "MEDIUM";

    @Column(nullable = false, length = 24)
    private String status = "OPEN";

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "identified_by", nullable = false, length = 100)
    private String identifiedBy;

    @Column(name = "identified_at", nullable = false)
    private OffsetDateTime identifiedAt = OffsetDateTime.now();

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolution_comment", columnDefinition = "TEXT")
    private String resolutionComment;

    public Situation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOriginType() { return originType; }
    public void setOriginType(String originType) { this.originType = originType; }
    public Long getOriginId() { return originId; }
    public void setOriginId(Long originId) { this.originId = originId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getCycleId() { return cycleId; }
    public void setCycleId(Long cycleId) { this.cycleId = cycleId; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIdentifiedBy() { return identifiedBy; }
    public void setIdentifiedBy(String identifiedBy) { this.identifiedBy = identifiedBy; }
    public OffsetDateTime getIdentifiedAt() { return identifiedAt; }
    public void setIdentifiedAt(OffsetDateTime identifiedAt) { this.identifiedAt = identifiedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolutionComment() { return resolutionComment; }
    public void setResolutionComment(String resolutionComment) { this.resolutionComment = resolutionComment; }
}
