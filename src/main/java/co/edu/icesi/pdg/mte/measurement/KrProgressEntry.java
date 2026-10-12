package co.edu.icesi.pdg.mte.measurement;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "kr_progress_entry")
public class KrProgressEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_result_id", nullable = false)
    private Long keyResultId;

    @Column(name = "progress_percent", precision = 5, scale = 2, nullable = false)
    private BigDecimal progressPercent;

    @Column(nullable = false, length = 24)
    private String status = "ON_TRACK";

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "declared_by", nullable = false, length = 100)
    private String declaredBy;

    @Column(name = "declared_at", nullable = false)
    private OffsetDateTime declaredAt = OffsetDateTime.now();

    public KrProgressEntry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKeyResultId() { return keyResultId; }
    public void setKeyResultId(Long keyResultId) { this.keyResultId = keyResultId; }
    public BigDecimal getProgressPercent() { return progressPercent; }
    public void setProgressPercent(BigDecimal progressPercent) { this.progressPercent = progressPercent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getDeclaredBy() { return declaredBy; }
    public void setDeclaredBy(String declaredBy) { this.declaredBy = declaredBy; }
    public OffsetDateTime getDeclaredAt() { return declaredAt; }
    public void setDeclaredAt(OffsetDateTime declaredAt) { this.declaredAt = declaredAt; }
}
