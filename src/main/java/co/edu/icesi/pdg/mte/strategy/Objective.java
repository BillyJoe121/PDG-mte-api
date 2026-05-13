package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.Department;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

@Entity
@Table(name = "objective")
public class Objective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ObjectiveStatus status = ObjectiveStatus.ACTIVO;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Long createdByExternalUserId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Department department;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AcademicPeriod academicPeriod;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private InstitutionalGoal goal;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private StrategicBet strategicBet;

    @OneToMany(mappedBy = "objective", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<KeyResult> keyResults = new ArrayList<>();

    public BigDecimal completionPercentage() {
        List<BigDecimal> progressValues = keyResults.stream()
                .map(KeyResult::getProgressPercentage)
                .filter(value -> value != null)
                .toList();
        if (progressValues.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = progressValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(progressValues.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    public void addKeyResult(KeyResult keyResult) {
        keyResult.setObjective(this);
        keyResults.add(keyResult);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ObjectiveStatus getStatus() {
        return status;
    }

    public void setStatus(ObjectiveStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedByExternalUserId() {
        return createdByExternalUserId;
    }

    public void setCreatedByExternalUserId(Long createdByExternalUserId) {
        this.createdByExternalUserId = createdByExternalUserId;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public AcademicPeriod getAcademicPeriod() {
        return academicPeriod;
    }

    public void setAcademicPeriod(AcademicPeriod academicPeriod) {
        this.academicPeriod = academicPeriod;
    }

    public InstitutionalGoal getGoal() {
        return goal;
    }

    public void setGoal(InstitutionalGoal goal) {
        this.goal = goal;
    }

    public StrategicBet getStrategicBet() {
        return strategicBet;
    }

    public void setStrategicBet(StrategicBet strategicBet) {
        this.strategicBet = strategicBet;
    }

    public List<KeyResult> getKeyResults() {
        return keyResults;
    }

    public void setKeyResults(List<KeyResult> keyResults) {
        this.keyResults = keyResults;
    }
}
