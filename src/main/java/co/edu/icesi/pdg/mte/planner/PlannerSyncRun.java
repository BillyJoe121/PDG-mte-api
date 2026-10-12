package co.edu.icesi.pdg.mte.planner;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "planner_sync_run")
public class PlannerSyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    private PlannerConnection connection;

    @Column(nullable = false, length = 24)
    private String state;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PlannerConnection getConnection() { return connection; }
    public void setConnection(PlannerConnection connection) { this.connection = connection; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
