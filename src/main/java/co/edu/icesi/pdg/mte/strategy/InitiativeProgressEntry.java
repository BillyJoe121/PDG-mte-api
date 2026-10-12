package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.people.Professor;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "initiative_progress_entry")
public class InitiativeProgressEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "initiative_id")
    private Initiative initiative;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, columnDefinition = "text")
    private String comment;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Professor actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_id")
    private InitiativeProgressEntry supersedes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Initiative getInitiative() { return initiative; }
    public void setInitiative(Initiative initiative) { this.initiative = initiative; }

    public BigDecimal getProgressPercent() { return progressPercent; }
    public void setProgressPercent(BigDecimal progressPercent) { this.progressPercent = progressPercent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Professor getActor() { return actor; }
    public void setActor(Professor actor) { this.actor = actor; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public InitiativeProgressEntry getSupersedes() { return supersedes; }
    public void setSupersedes(InitiativeProgressEntry supersedes) { this.supersedes = supersedes; }
}
