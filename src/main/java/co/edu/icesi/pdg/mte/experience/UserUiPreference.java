package co.edu.icesi.pdg.mte.experience;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_ui_preference")
public class UserUiPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 100)
    private String userId;

    @Column(name = "reduced_motion", nullable = false)
    private Boolean reducedMotion = false;

    @Column(name = "high_contrast", nullable = false)
    private Boolean highContrast = false;

    @Column(name = "preferred_view_mode", nullable = false, length = 30)
    private String preferredViewMode = "EXECUTIVE";

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public UserUiPreference() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Boolean getReducedMotion() { return reducedMotion; }
    public void setReducedMotion(Boolean reducedMotion) { this.reducedMotion = reducedMotion; }
    public Boolean getHighContrast() { return highContrast; }
    public void setHighContrast(Boolean highContrast) { this.highContrast = highContrast; }
    public String getPreferredViewMode() { return preferredViewMode; }
    public void setPreferredViewMode(String preferredViewMode) { this.preferredViewMode = preferredViewMode; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
