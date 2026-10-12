package co.edu.icesi.pdg.mte.planner;

import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.strategy.Initiative;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "planner_task_ref")
public class PlannerTaskRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    private PlannerConnection connection;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "initiative_id")
    private Initiative initiative;

    @Column(nullable = false, length = 32)
    private String provider = "MICROSOFT_GRAPH";

    @Column(name = "external_plan_id", nullable = false, columnDefinition = "text")
    private String externalPlanId;

    @Column(name = "external_task_id", nullable = false, columnDefinition = "text")
    private String externalTaskId;

    @Column(name = "external_url", columnDefinition = "text")
    private String externalUrl;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_by")
    private Professor linkedBy;

    @Column(name = "title_snapshot", columnDefinition = "text")
    private String titleSnapshot;

    @Column(name = "bucket_snapshot", columnDefinition = "text")
    private String bucketSnapshot;

    @Column(name = "status_snapshot", length = 32)
    private String statusSnapshot;

    @Column(name = "due_date_snapshot")
    private Instant dueDateSnapshot;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PlannerConnection getConnection() { return connection; }
    public void setConnection(PlannerConnection connection) { this.connection = connection; }

    public Initiative getInitiative() { return initiative; }
    public void setInitiative(Initiative initiative) { this.initiative = initiative; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getExternalPlanId() { return externalPlanId; }
    public void setExternalPlanId(String externalPlanId) { this.externalPlanId = externalPlanId; }

    public String getExternalTaskId() { return externalTaskId; }
    public void setExternalTaskId(String externalTaskId) { this.externalTaskId = externalTaskId; }

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    public Professor getLinkedBy() { return linkedBy; }
    public void setLinkedBy(Professor linkedBy) { this.linkedBy = linkedBy; }

    public String getTitleSnapshot() { return titleSnapshot; }
    public void setTitleSnapshot(String titleSnapshot) { this.titleSnapshot = titleSnapshot; }

    public String getBucketSnapshot() { return bucketSnapshot; }
    public void setBucketSnapshot(String bucketSnapshot) { this.bucketSnapshot = bucketSnapshot; }

    public String getStatusSnapshot() { return statusSnapshot; }
    public void setStatusSnapshot(String statusSnapshot) { this.statusSnapshot = statusSnapshot; }

    public Instant getDueDateSnapshot() { return dueDateSnapshot; }
    public void setDueDateSnapshot(Instant dueDateSnapshot) { this.dueDateSnapshot = dueDateSnapshot; }

    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
