package co.edu.icesi.pdg.mte.measurement;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cycle_closure")
public class CycleClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false, unique = true)
    private Long cycleId;

    @Column(name = "rector_review_id")
    private Long rectorReviewId;

    @Column(name = "closure_status", nullable = false, length = 24)
    private String closureStatus = "CLOSED";

    @Column(name = "closed_by", nullable = false, length = 100)
    private String closedBy;

    @Column(name = "closed_at", nullable = false)
    private OffsetDateTime closedAt = OffsetDateTime.now();

    @Column(name = "retrospective_achievements", nullable = false, columnDefinition = "TEXT")
    private String retrospectiveAchievements;

    @Column(name = "retrospective_causes", nullable = false, columnDefinition = "TEXT")
    private String retrospectiveCauses;

    @Column(name = "retrospective_learnings", nullable = false, columnDefinition = "TEXT")
    private String retrospectiveLearnings;

    @Column(name = "retrospective_continuity", nullable = false, columnDefinition = "TEXT")
    private String retrospectiveContinuity;

    public CycleClosure() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCycleId() { return cycleId; }
    public void setCycleId(Long cycleId) { this.cycleId = cycleId; }
    public Long getRectorReviewId() { return rectorReviewId; }
    public void setRectorReviewId(Long rectorReviewId) { this.rectorReviewId = rectorReviewId; }
    public String getClosureStatus() { return closureStatus; }
    public void setClosureStatus(String closureStatus) { this.closureStatus = closureStatus; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public String getRetrospectiveAchievements() { return retrospectiveAchievements; }
    public void setRetrospectiveAchievements(String retrospectiveAchievements) { this.retrospectiveAchievements = retrospectiveAchievements; }
    public String getRetrospectiveCauses() { return retrospectiveCauses; }
    public void setRetrospectiveCauses(String retrospectiveCauses) { this.retrospectiveCauses = retrospectiveCauses; }
    public String getRetrospectiveLearnings() { return retrospectiveLearnings; }
    public void setRetrospectiveLearnings(String retrospectiveLearnings) { this.retrospectiveLearnings = retrospectiveLearnings; }
    public String getRetrospectiveContinuity() { return retrospectiveContinuity; }
    public void setRetrospectiveContinuity(String retrospectiveContinuity) { this.retrospectiveContinuity = retrospectiveContinuity; }
}
