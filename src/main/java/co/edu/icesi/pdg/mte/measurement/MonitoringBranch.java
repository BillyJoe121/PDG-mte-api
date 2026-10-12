package co.edu.icesi.pdg.mte.measurement;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "monitoring_branch")
public class MonitoringBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cut_id", nullable = false)
    private Long cutId;

    @Column(name = "branch_type", nullable = false, length = 32)
    private String branchType;

    @Column(nullable = false, length = 32)
    private String status = "OPEN";

    @Column(name = "as_of", nullable = false)
    private OffsetDateTime asOf = OffsetDateTime.now();

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "department_name")
    private String departmentName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public MonitoringBranch() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCutId() { return cutId; }
    public void setCutId(Long cutId) { this.cutId = cutId; }

    public String getBranchType() { return branchType; }
    public void setBranchType(String branchType) { this.branchType = branchType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getAsOf() { return asOf; }
    public void setAsOf(OffsetDateTime asOf) { this.asOf = asOf; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
