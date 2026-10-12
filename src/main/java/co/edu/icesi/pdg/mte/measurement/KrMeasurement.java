package co.edu.icesi.pdg.mte.measurement;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "kr_measurement")
public class KrMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_result_id", nullable = false)
    private Long keyResultId;

    @Column(name = "measured_value", precision = 12, scale = 4, nullable = false)
    private BigDecimal measuredValue;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "registered_by", length = 100, nullable = false)
    private String registeredBy;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt = OffsetDateTime.now();

    @Column(name = "status", length = 24, nullable = false)
    private String status = "ACTIVE";

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public KrMeasurement() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKeyResultId() { return keyResultId; }
    public void setKeyResultId(Long keyResultId) { this.keyResultId = keyResultId; }
    public BigDecimal getMeasuredValue() { return measuredValue; }
    public void setMeasuredValue(BigDecimal measuredValue) { this.measuredValue = measuredValue; }
    public OffsetDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(OffsetDateTime observedAt) { this.observedAt = observedAt; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(String registeredBy) { this.registeredBy = registeredBy; }
    public OffsetDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(OffsetDateTime registeredAt) { this.registeredAt = registeredAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
