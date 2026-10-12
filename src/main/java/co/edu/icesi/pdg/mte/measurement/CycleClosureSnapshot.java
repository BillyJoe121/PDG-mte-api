package co.edu.icesi.pdg.mte.measurement;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cycle_closure_snapshot")
public class CycleClosureSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_closure_id", nullable = false)
    private Long cycleClosureId;

    @Column(name = "key_result_id", nullable = false)
    private Long keyResultId;

    @Column(name = "kr_name", nullable = false, length = 255)
    private String krName;

    @Column(name = "final_measured_value", precision = 12, scale = 4)
    private BigDecimal finalMeasuredValue;

    @Column(name = "final_manual_progress", precision = 5, scale = 2)
    private BigDecimal finalManualProgress;

    @Column(name = "final_status", nullable = false, length = 24)
    private String finalStatus;

    public CycleClosureSnapshot() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCycleClosureId() { return cycleClosureId; }
    public void setCycleClosureId(Long cycleClosureId) { this.cycleClosureId = cycleClosureId; }
    public Long getKeyResultId() { return keyResultId; }
    public void setKeyResultId(Long keyResultId) { this.keyResultId = keyResultId; }
    public String getKrName() { return krName; }
    public void setKrName(String krName) { this.krName = krName; }
    public BigDecimal getFinalMeasuredValue() { return finalMeasuredValue; }
    public void setFinalMeasuredValue(BigDecimal finalMeasuredValue) { this.finalMeasuredValue = finalMeasuredValue; }
    public BigDecimal getFinalManualProgress() { return finalManualProgress; }
    public void setFinalManualProgress(BigDecimal finalManualProgress) { this.finalManualProgress = finalManualProgress; }
    public String getFinalStatus() { return finalStatus; }
    public void setFinalStatus(String finalStatus) { this.finalStatus = finalStatus; }
}
