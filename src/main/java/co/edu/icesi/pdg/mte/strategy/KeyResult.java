package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnit;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    @Column(length = 2048)
    private String plannerUrl;

    @Column(nullable = false)
    private boolean archived = false;

    private Instant archivedAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_of_measure_id")
    private MeasurementUnit measurementUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_period_id")
    private AcademicPeriod academicPeriod;

    @ManyToMany
    @JoinTable(
            name = "key_result_goal",
            joinColumns = @JoinColumn(name = "key_result_id"),
            inverseJoinColumns = @JoinColumn(name = "goal_id")
    )
    private Set<InstitutionalGoal> goals = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "key_result_strategic_bet",
            joinColumns = @JoinColumn(name = "key_result_id"),
            inverseJoinColumns = @JoinColumn(name = "strategic_bet_id")
    )
    private Set<StrategicBet> strategicBets = new LinkedHashSet<>();

    @OneToMany(mappedBy = "keyResult")
    private List<ProjectKeyResultLink> projectLinks = new ArrayList<>();

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

    public String getPlannerUrl() {
        return plannerUrl;
    }

    public void setPlannerUrl(String plannerUrl) {
        this.plannerUrl = plannerUrl;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
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

    public Set<InstitutionalGoal> getGoals() {
        return goals;
    }

    public void setGoals(Set<InstitutionalGoal> goals) {
        this.goals = goals;
    }

    public Set<StrategicBet> getStrategicBets() {
        return strategicBets;
    }

    public void setStrategicBets(Set<StrategicBet> strategicBets) {
        this.strategicBets = strategicBets;
    }

    public List<ProjectKeyResultLink> getProjectLinks() {
        return projectLinks;
    }

    public void setProjectLinks(List<ProjectKeyResultLink> projectLinks) {
        this.projectLinks = projectLinks;
    }
}
