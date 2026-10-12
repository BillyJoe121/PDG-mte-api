package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.people.Professor;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "kr_version")
public class KrVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "key_result_id")
    private KeyResult keyResult;

    @Column(nullable = false)
    private Long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Professor actor;

    @Column(nullable = false)
    private Instant occurredAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public KeyResult getKeyResult() { return keyResult; }
    public void setKeyResult(KeyResult keyResult) { this.keyResult = keyResult; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Professor getActor() { return actor; }
    public void setActor(Professor actor) { this.actor = actor; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
