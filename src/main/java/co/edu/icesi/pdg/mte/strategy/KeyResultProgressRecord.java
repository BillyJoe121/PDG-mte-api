package co.edu.icesi.pdg.mte.strategy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** One registered progress update of a Key Result, kept as history with who made it and when. */
@Entity
@Table(name = "key_result_progress_record")
public class KeyResultProgressRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long keyResultId;

    @Column(precision = 12, scale = 2)
    private BigDecimal previousValue;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal currentValue;

    @Column(precision = 10, scale = 2)
    private BigDecimal previousPercentage;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal progressPercentage;

    private String authorName;

    private String authorEmail;

    @Column(nullable = false)
    private Instant recordedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKeyResultId() {
        return keyResultId;
    }

    public void setKeyResultId(Long keyResultId) {
        this.keyResultId = keyResultId;
    }

    public BigDecimal getPreviousValue() {
        return previousValue;
    }

    public void setPreviousValue(BigDecimal previousValue) {
        this.previousValue = previousValue;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public BigDecimal getPreviousPercentage() {
        return previousPercentage;
    }

    public void setPreviousPercentage(BigDecimal previousPercentage) {
        this.previousPercentage = previousPercentage;
    }

    public BigDecimal getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(BigDecimal progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
