package co.edu.icesi.pdg.mte.project;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "project_progress_entry")
public class ProjectProgressEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Project project;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comment;

    @Column(columnDefinition = "TEXT")
    private String milestones;

    private Long createdByExternalUserId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public BigDecimal getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(BigDecimal progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getMilestones() {
        return milestones;
    }

    public void setMilestones(String milestones) {
        this.milestones = milestones;
    }

    public Long getCreatedByExternalUserId() {
        return createdByExternalUserId;
    }

    public void setCreatedByExternalUserId(Long createdByExternalUserId) {
        this.createdByExternalUserId = createdByExternalUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
