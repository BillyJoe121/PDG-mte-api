package co.edu.icesi.pdg.mte.audit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "retention_hold")
public class RetentionHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "hold_status", nullable = false, length = 24)
    private String holdStatus = "ACTIVE";

    @Column(name = "applied_by", nullable = false, length = 100)
    private String appliedBy;

    @Column(name = "applied_at", nullable = false)
    private OffsetDateTime appliedAt = OffsetDateTime.now();

    @Column(name = "released_by", length = 100)
    private String releasedBy;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    public RetentionHold() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getHoldStatus() { return holdStatus; }
    public void setHoldStatus(String holdStatus) { this.holdStatus = holdStatus; }
    public String getAppliedBy() { return appliedBy; }
    public void setAppliedBy(String appliedBy) { this.appliedBy = appliedBy; }
    public OffsetDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(OffsetDateTime appliedAt) { this.appliedAt = appliedAt; }
    public String getReleasedBy() { return releasedBy; }
    public void setReleasedBy(String releasedBy) { this.releasedBy = releasedBy; }
    public OffsetDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(OffsetDateTime releasedAt) { this.releasedAt = releasedAt; }
}
