package co.edu.icesi.pdg.mte.reports;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "presentation_session")
public class PresentationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "cut_id")
    private Long cutId;

    @Column(name = "presenter_id", nullable = false, length = 100)
    private String presenterId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "active_slide", nullable = false, length = 50)
    private String activeSlide = "SUMMARY";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public PresentationSession() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCycleId() { return cycleId; }
    public void setCycleId(Long cycleId) { this.cycleId = cycleId; }
    public Long getCutId() { return cutId; }
    public void setCutId(Long cutId) { this.cutId = cutId; }
    public String getPresenterId() { return presenterId; }
    public void setPresenterId(String presenterId) { this.presenterId = presenterId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getActiveSlide() { return activeSlide; }
    public void setActiveSlide(String activeSlide) { this.activeSlide = activeSlide; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
