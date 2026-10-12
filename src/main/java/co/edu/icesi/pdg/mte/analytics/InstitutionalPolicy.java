package co.edu.icesi.pdg.mte.analytics;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "institutional_policy", uniqueConstraints = @UniqueConstraint(columnNames = {"policy_key", "version"}))
public class InstitutionalPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_code", length = 100)
    private String policyCode;

    @Column(name = "policy_key", nullable = false, length = 64)
    private String policyKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "\"value\"", nullable = false, columnDefinition = "jsonb")
    private String value;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "approved_by", nullable = false)
    private Long approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "effective_at", nullable = false)
    private OffsetDateTime effectiveAt = OffsetDateTime.now();

    public InstitutionalPolicy() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPolicyCode() { return policyCode; }
    public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }
    public String getPolicyKey() { return policyKey; }
    public void setPolicyKey(String policyKey) { this.policyKey = policyKey; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
    public OffsetDateTime getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(OffsetDateTime effectiveAt) { this.effectiveAt = effectiveAt; }
}
