package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "project",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_external_source_id",
                columnNames = {"external_source", "external_project_id"}
        )
)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long externalProjectId;

    @Column(name = "external_source")
    private String externalSource;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private ProjectType type;

    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;

    private String departmentName;

    @Enumerated(EnumType.STRING)
    private ProjectStatus status = ProjectStatus.BORRADOR;

    @Column(nullable = false)
    private String startPeriod;

    @Column(nullable = false)
    private String endPeriod;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDate actualEndDate;

    private String jiraKey;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private KeyResult keyResult;

    @Column(precision = 5, scale = 2)
    private BigDecimal contributionWeight;

    private String linkStatus;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal globalProgress = BigDecimal.ZERO;

    @ElementCollection
    @CollectionTable(name = "project_tutor", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "tutor_name", nullable = false)
    private List<String> tutors = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectOrigin origin = ProjectOrigin.LOCAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectSyncStatus syncStatus = ProjectSyncStatus.LOCAL_ONLY;

    private Instant lastSyncedAt;

    @Column(columnDefinition = "TEXT")
    private String rawExternalPayload;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExternalProjectId() {
        return externalProjectId;
    }

    public void setExternalProjectId(Long externalProjectId) {
        this.externalProjectId = externalProjectId;
    }

    public String getExternalSource() {
        return externalSource;
    }

    public void setExternalSource(String externalSource) {
        this.externalSource = externalSource;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProjectType getType() {
        return type;
    }

    public void setType(ProjectType type) {
        this.type = type;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public String getStartPeriod() {
        return startPeriod;
    }

    public void setStartPeriod(String startPeriod) {
        this.startPeriod = startPeriod;
    }

    public String getEndPeriod() {
        return endPeriod;
    }

    public void setEndPeriod(String endPeriod) {
        this.endPeriod = endPeriod;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalDate getActualEndDate() {
        return actualEndDate;
    }

    public void setActualEndDate(LocalDate actualEndDate) {
        this.actualEndDate = actualEndDate;
    }

    public String getJiraKey() {
        return jiraKey;
    }

    public void setJiraKey(String jiraKey) {
        this.jiraKey = jiraKey;
    }

    public KeyResult getKeyResult() {
        return keyResult;
    }

    public void setKeyResult(KeyResult keyResult) {
        this.keyResult = keyResult;
    }

    public BigDecimal getContributionWeight() {
        return contributionWeight;
    }

    public void setContributionWeight(BigDecimal contributionWeight) {
        this.contributionWeight = contributionWeight;
    }

    public String getLinkStatus() {
        return linkStatus;
    }

    public void setLinkStatus(String linkStatus) {
        this.linkStatus = linkStatus;
    }

    public BigDecimal getGlobalProgress() {
        return globalProgress;
    }

    public void setGlobalProgress(BigDecimal globalProgress) {
        this.globalProgress = globalProgress;
    }

    public List<String> getTutors() {
        return tutors;
    }

    public void setTutors(List<String> tutors) {
        this.tutors = tutors == null ? new ArrayList<>() : new ArrayList<>(tutors);
    }

    public ProjectOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(ProjectOrigin origin) {
        this.origin = origin;
    }

    public ProjectSyncStatus getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(ProjectSyncStatus syncStatus) {
        this.syncStatus = syncStatus;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getRawExternalPayload() {
        return rawExternalPayload;
    }

    public void setRawExternalPayload(String rawExternalPayload) {
        this.rawExternalPayload = rawExternalPayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
