package co.edu.icesi.pdg.mte.operations;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "dependency_exception")
public class DependencyException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "finding_id", nullable = false, length = 128)
    private String findingId;

    @Column(name = "cve_id", nullable = false, length = 64)
    private String cveId;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "approved_by", nullable = false)
    private String approvedBy;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public DependencyException() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFindingId() { return findingId; }
    public void setFindingId(String findingId) { this.findingId = findingId; }

    public String getCveId() { return cveId; }
    public void setCveId(String cveId) { this.cveId = cveId; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
