package co.edu.icesi.pdg.mte.analytics;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "situation_history")
public class SituationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "situation_id", nullable = false)
    private Long situationId;

    @Column(name = "previous_status", nullable = false, length = 24)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 24)
    private String newStatus;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "evidence_id")
    private Long evidenceId;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt = OffsetDateTime.now();

    public SituationHistory() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSituationId() { return situationId; }
    public void setSituationId(Long situationId) { this.situationId = situationId; }
    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Long getEvidenceId() { return evidenceId; }
    public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public OffsetDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(OffsetDateTime changedAt) { this.changedAt = changedAt; }
}
