package co.edu.icesi.pdg.mte.measurement;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "kr_confidence_entry")
public class KrConfidenceEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_result_id", nullable = false)
    private Long keyResultId;

    @Column(name = "confidence_level", nullable = false, length = 20)
    private String confidenceLevel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comment;

    @Column(name = "author_id", nullable = false, length = 100)
    private String authorId;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    public KrConfidenceEntry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKeyResultId() { return keyResultId; }
    public void setKeyResultId(Long keyResultId) { this.keyResultId = keyResultId; }
    public String getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(String confidenceLevel) { this.confidenceLevel = confidenceLevel; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
