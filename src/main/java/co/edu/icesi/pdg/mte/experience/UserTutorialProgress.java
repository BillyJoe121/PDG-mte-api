package co.edu.icesi.pdg.mte.experience;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_tutorial_progress", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "role_context"})
})
public class UserTutorialProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "role_context", nullable = false, length = 50)
    private String roleContext;

    @Column(name = "current_step_index", nullable = false)
    private Integer currentStepIndex = 0;

    @Column(name = "completed_steps_json", nullable = false, columnDefinition = "TEXT")
    private String completedStepsJson = "[]";

    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    @Column(name = "is_skipped", nullable = false)
    private Boolean isSkipped = false;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public UserTutorialProgress() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRoleContext() { return roleContext; }
    public void setRoleContext(String roleContext) { this.roleContext = roleContext; }
    public Integer getCurrentStepIndex() { return currentStepIndex; }
    public void setCurrentStepIndex(Integer currentStepIndex) { this.currentStepIndex = currentStepIndex; }
    public String getCompletedStepsJson() { return completedStepsJson; }
    public void setCompletedStepsJson(String completedStepsJson) { this.completedStepsJson = completedStepsJson; }
    public Boolean getIsCompleted() { return isCompleted; }
    public void setIsCompleted(Boolean isCompleted) { this.isCompleted = isCompleted; }
    public Boolean getIsSkipped() { return isSkipped; }
    public void setIsSkipped(Boolean isSkipped) { this.isSkipped = isSkipped; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
