package co.edu.icesi.pdg.mte.measurement;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "monitoring_cut")
public class MonitoringCut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "cut_code", nullable = false, length = 50)
    private String cutCode;

    @Column(name = "cut_date", nullable = false)
    private LocalDate cutDate;

    @Column(nullable = false, length = 24)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public MonitoringCut() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCycleId() { return cycleId; }
    public void setCycleId(Long cycleId) { this.cycleId = cycleId; }
    public String getCutCode() { return cutCode; }
    public void setCutCode(String cutCode) { this.cutCode = cutCode; }
    public LocalDate getCutDate() { return cutDate; }
    public void setCutDate(LocalDate cutDate) { this.cutDate = cutDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
