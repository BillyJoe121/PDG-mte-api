package co.edu.icesi.pdg.mte.measurement;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "rector_review")
public class RectorReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitoring_cut_id", nullable = false)
    private Long monitoringCutId;

    @Column(name = "review_version", nullable = false)
    private Integer reviewVersion = 1;

    @Column(nullable = false, length = 24)
    private String status = "APPROVED";

    @Column(name = "reviewed_by", nullable = false, length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt = OffsetDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String observations;

    public RectorReview() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMonitoringCutId() { return monitoringCutId; }
    public void setMonitoringCutId(Long monitoringCutId) { this.monitoringCutId = monitoringCutId; }
    public Integer getReviewVersion() { return reviewVersion; }
    public void setReviewVersion(Integer reviewVersion) { this.reviewVersion = reviewVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }
}
