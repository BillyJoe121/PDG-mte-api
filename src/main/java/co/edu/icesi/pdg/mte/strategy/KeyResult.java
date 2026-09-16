package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnit;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "key_result")
public class KeyResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String metric;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal baseValue = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal targetValue;

    @Column(precision = 12, scale = 2)
    private BigDecimal currentValue;

    @Column(precision = 10, scale = 2)
    private BigDecimal progressPercentage = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KeyResultStatus status = KeyResultStatus.BORRADOR;

    private Instant createdAt = Instant.now();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_of_measure_id")
    private MeasurementUnit measurementUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_period_id")
    private AcademicPeriod academicPeriod;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Objective objective;

    @OneToMany(mappedBy = "keyResult")
    private List<ProjectKeyResultLink> projectLinks = new ArrayList<>();

    public void recalculateProgress() {
        if (baseValue == null || targetValue == null || currentValue == null
                || targetValue.compareTo(baseValue) == 0) {
            progressPercentage = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return;
        }
        BigDecimal rawProgress = currentValue.subtract(baseValue)
                .multiply(BigDecimal.valueOf(100))
                .divide(targetValue.subtract(baseValue), 2, RoundingMode.HALF_UP);
        progressPercentage = rawProgress.max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public void recalculateProgress(List<ProjectKeyResultLink> activeLinks) {
        recalculateProgress();
    }

    @PrePersist
    @PreUpdate
    void beforeSave() {
        recalculateProgress();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public BigDecimal getBaseValue() {
        return baseValue;
    }

    public void setBaseValue(BigDecimal baseValue) {
        this.baseValue = baseValue;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public BigDecimal getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(BigDecimal progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public KeyResultStatus getStatus() {
        return status;
    }

    public void setStatus(KeyResultStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public MeasurementUnit getMeasurementUnit() {
        return measurementUnit;
    }

    public void setMeasurementUnit(MeasurementUnit measurementUnit) {
        this.measurementUnit = measurementUnit;
    }

    public AcademicPeriod getAcademicPeriod() {
        return academicPeriod;
    }

    public void setAcademicPeriod(AcademicPeriod academicPeriod) {
        this.academicPeriod = academicPeriod;
    }

    public Objective getObjective() {
        return objective;
    }

    public void setObjective(Objective objective) {
        this.objective = objective;
    }

    public List<ProjectKeyResultLink> getProjectLinks() {
        return projectLinks;
    }

    public void setProjectLinks(List<ProjectKeyResultLink> projectLinks) {
        this.projectLinks = projectLinks;
    }
}
