package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.people.Professor;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "kr_goal_link")
public class KrGoalLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "key_result_id")
    private KeyResult keyResult;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private InstitutionalGoal goal;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant endedAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Professor actor;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public KeyResult getKeyResult() { return keyResult; }
    public void setKeyResult(KeyResult keyResult) { this.keyResult = keyResult; }

    public InstitutionalGoal getGoal() { return goal; }
    public void setGoal(InstitutionalGoal goal) { this.goal = goal; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public Professor getActor() { return actor; }
    public void setActor(Professor actor) { this.actor = actor; }
}
