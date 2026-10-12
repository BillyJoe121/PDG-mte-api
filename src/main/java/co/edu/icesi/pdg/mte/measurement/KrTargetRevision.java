package co.edu.icesi.pdg.mte.measurement;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "kr_target_revision")
public class KrTargetRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_result_id", nullable = false)
    private Long keyResultId;

    @Column(name = "previous_baseline", precision = 12, scale = 4, nullable = false)
    private BigDecimal previousBaseline;

    @Column(name = "new_baseline", precision = 12, scale = 4, nullable = false)
    private BigDecimal newBaseline;

    @Column(name = "previous_target", precision = 12, scale = 4, nullable = false)
    private BigDecimal previousTarget;

    @Column(name = "new_target", precision = 12, scale = 4, nullable = false)
    private BigDecimal newTarget;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "effective_from_check_in", length = 100)
    private String effectiveFromCheckIn;

    @Column(name = "approval_status", nullable = false, length = 30)
    private String approvalStatus = "PENDING";

    @Column(name = "policy_unset", nullable = false)
    private Boolean policyUnset = false;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();

    public KrTargetRevision() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKeyResultId() { return keyResultId; }
    public void setKeyResultId(Long keyResultId) { this.keyResultId = keyResultId; }
    public BigDecimal getPreviousBaseline() { return previousBaseline; }
    public void setPreviousBaseline(BigDecimal previousBaseline) { this.previousBaseline = previousBaseline; }
    public BigDecimal getNewBaseline() { return newBaseline; }
    public void setNewBaseline(BigDecimal newBaseline) { this.newBaseline = newBaseline; }
    public BigDecimal getPreviousTarget() { return previousTarget; }
    public void setPreviousTarget(BigDecimal previousTarget) { this.previousTarget = previousTarget; }
    public BigDecimal getNewTarget() { return newTarget; }
    public void setNewTarget(BigDecimal newTarget) { this.newTarget = newTarget; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getEffectiveFromCheckIn() { return effectiveFromCheckIn; }
    public void setEffectiveFromCheckIn(String effectiveFromCheckIn) { this.effectiveFromCheckIn = effectiveFromCheckIn; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public Boolean getPolicyUnset() { return policyUnset; }
    public void setPolicyUnset(Boolean policyUnset) { this.policyUnset = policyUnset; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }
}
