package co.edu.icesi.pdg.mte.strategy;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "guardrail")
public class Guardrail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "metric_definition_id", nullable = false)
    private Long metricDefinitionId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private GuardrailComparison comparison;

    @Column(nullable = false)
    private Boolean active = true;

    @ManyToMany
    @JoinTable(
        name = "guardrail_kr_link",
        joinColumns = @JoinColumn(name = "guardrail_id"),
        inverseJoinColumns = @JoinColumn(name = "key_result_id")
    )
    private Set<KeyResult> keyResults = new HashSet<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getMetricDefinitionId() { return metricDefinitionId; }
    public void setMetricDefinitionId(Long metricDefinitionId) { this.metricDefinitionId = metricDefinitionId; }

    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }

    public GuardrailComparison getComparison() { return comparison; }
    public void setComparison(GuardrailComparison comparison) { this.comparison = comparison; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Set<KeyResult> getKeyResults() { return keyResults; }
    public void setKeyResults(Set<KeyResult> keyResults) { this.keyResults = keyResults; }
}
