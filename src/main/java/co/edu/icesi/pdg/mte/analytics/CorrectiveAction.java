package co.edu.icesi.pdg.mte.analytics;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "corrective_action")
public class CorrectiveAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "situation_id", nullable = false)
    private Long situationId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "assigned_to", nullable = false, length = 100)
    private String assignedTo;

    @Column(name = "due_at", nullable = false)
    private LocalDate dueAt;

    @Column(name = "decision_description", nullable = false, columnDefinition = "TEXT")
    private String decisionDescription;

    @Column(nullable = false, length = 24)
    private String status = "PENDING";

    @Column(name = "evidence_id")
    private Long evidenceId;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    public CorrectiveAction() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSituationId() { return situationId; }
    public void setSituationId(Long situationId) { this.situationId = situationId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public LocalDate getDueAt() { return dueAt; }
    public void setDueAt(LocalDate dueAt) { this.dueAt = dueAt; }
    public String getDecisionDescription() { return decisionDescription; }
    public void setDecisionDescription(String decisionDescription) { this.decisionDescription = decisionDescription; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getEvidenceId() { return evidenceId; }
    public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
}
