package co.edu.icesi.pdg.mte.reports;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "school_final_report")
public class SchoolFinalReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "executive_summary", nullable = false, columnDefinition = "TEXT")
    private String executiveSummary;

    @Column(name = "achievements_summary", nullable = false, columnDefinition = "TEXT")
    private String achievementsSummary;

    @Column(name = "challenges_summary", nullable = false, columnDefinition = "TEXT")
    private String challengesSummary;

    @Column(nullable = false, length = 24)
    private String status = "DRAFT";

    @Column(name = "author_id", nullable = false, length = 100)
    private String authorId;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public SchoolFinalReport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCycleId() { return cycleId; }
    public void setCycleId(Long cycleId) { this.cycleId = cycleId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getExecutiveSummary() { return executiveSummary; }
    public void setExecutiveSummary(String executiveSummary) { this.executiveSummary = executiveSummary; }
    public String getAchievementsSummary() { return achievementsSummary; }
    public void setAchievementsSummary(String achievementsSummary) { this.achievementsSummary = achievementsSummary; }
    public String getChallengesSummary() { return challengesSummary; }
    public void setChallengesSummary(String challengesSummary) { this.challengesSummary = challengesSummary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
