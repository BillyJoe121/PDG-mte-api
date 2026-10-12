package co.edu.icesi.pdg.mte.governance;

import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class GovernanceV2Service {
    public record CycleWrite(String name, LocalDate startDate, LocalDate endDate, Integer cadenceDays,
                             String status, List<Long> periodIds, Long version) {}
    public record Cycle(long id, String name, LocalDate startDate, LocalDate endDate, int cadenceDays,
                        String status, long version, List<Long> periodIds, String createdAt, String updatedAt) {}
    public record MetricWrite(String name, Long unitOfMeasureId, String direction, String sourceDescription,
                              Integer frequencyDays, String numeratorDescription, String denominatorDescription,
                              String inclusionRules, String exclusionRules, Boolean active, Long version) {}
    public record Metric(long id, String name, long unitOfMeasureId, String direction, String sourceDescription,
                         long version, Integer frequencyDays, String numeratorDescription, String denominatorDescription,
                         String inclusionRules, String exclusionRules, boolean active, String createdAt, String updatedAt) {}
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public GovernanceV2Service(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    @Transactional(readOnly = true)
    public List<Cycle> cycles() {
        return jdbc.query("SELECT * FROM strategic_cycle ORDER BY start_date DESC, id DESC", (rs, row) -> cycle(rs));
    }
    private Cycle cycle(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        return new Cycle(id, rs.getString("name"), rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate(), rs.getInt("cadence_days"), rs.getString("status"), rs.getLong("version"),
                jdbc.queryForList("SELECT period_id FROM strategic_cycle_period WHERE cycle_id=? ORDER BY period_id", Long.class, id),
                rs.getTimestamp("created_at").toInstant().toString(), rs.getTimestamp("updated_at").toInstant().toString());
    }
    private Cycle findCycle(long id) {
        return jdbc.query("SELECT * FROM strategic_cycle WHERE id=?", (rs, row) -> cycle(rs), id).stream()
                .findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "CYCLE_NOT_FOUND"));
    }
    public Cycle saveCycle(Long id, CycleWrite r) {
        required(r.name(), "NAME_REQUIRED");
        if (r.startDate() == null || r.endDate() == null || r.startDate().isAfter(r.endDate()))
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DATE_RANGE");
        if (r.cadenceDays() == null || r.cadenceDays() <= 0) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CADENCE");
        String status = r.status() == null ? "DRAFT" : r.status();
        if (!Set.of("DRAFT", "ACTIVE").contains(status)) throw error(HttpStatus.CONFLICT, "FINAL_REVIEW_REQUIRED");
        List<Long> periods = r.periodIds() == null ? List.of() : r.periodIds().stream().distinct().toList();
        for (Long period : periods) {
            if (period == null || jdbc.queryForObject("SELECT COUNT(*) FROM period WHERE id=? AND active=TRUE", Long.class, period) != 1)
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PERIOD");
        }
        Cycle before = id == null ? null : findCycle(id);
        if (before != null && "CLOSED".equals(before.status())) throw error(HttpStatus.CONFLICT, "CYCLE_CLOSED");
        duplicate("strategic_cycle", r.name(), id);
        if (id == null) {
            id = new SimpleJdbcInsert(jdbc).withTableName("strategic_cycle").usingColumns("name", "start_date", "end_date", "cadence_days", "status")
                    .usingGeneratedKeyColumns("id").executeAndReturnKey(Map.of("name", r.name().trim(), "start_date", java.sql.Date.valueOf(r.startDate()),
                            "end_date", java.sql.Date.valueOf(r.endDate()), "cadence_days", r.cadenceDays(), "status", status)).longValue();
        } else {
            if (r.version() == null || jdbc.update("UPDATE strategic_cycle SET name=?, start_date=?, end_date=?, cadence_days=?, status=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?",
                    r.name().trim(), r.startDate(), r.endDate(), r.cadenceDays(), status, id, r.version()) != 1)
                throw error(HttpStatus.CONFLICT, "VERSION_CONFLICT");
            jdbc.update("DELETE FROM strategic_cycle_period WHERE cycle_id=?", id);
        }
        for (Long period : periods) jdbc.update("INSERT INTO strategic_cycle_period(cycle_id, period_id) VALUES (?,?)", id, period);
        Cycle after = findCycle(id);
        audit.record(before == null ? AuditAction.CREATE : AuditAction.UPDATE, "STRATEGIC_CYCLE", id, "Ciclo estratégico: " + after.name(), before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public List<Metric> metrics() {
        return jdbc.query("SELECT * FROM metric_definition ORDER BY name, id", (rs, row) -> metric(rs));
    }
    private Metric metric(ResultSet rs) throws SQLException {
        return new Metric(rs.getLong("id"), rs.getString("name"), rs.getLong("unit_of_measure_id"), rs.getString("direction"),
                rs.getString("source_description"), rs.getLong("version"), rs.getObject("frequency_days", Integer.class),
                rs.getString("numerator_description"), rs.getString("denominator_description"), rs.getString("inclusion_rules"), rs.getString("exclusion_rules"),
                rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant().toString(), rs.getTimestamp("updated_at").toInstant().toString());
    }
    private Metric findMetric(long id) {
        return jdbc.query("SELECT * FROM metric_definition WHERE id=?", (rs, row) -> metric(rs), id).stream()
                .findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "METRIC_NOT_FOUND"));
    }
    public Metric saveMetric(Long id, MetricWrite r) {
        required(r.name(), "NAME_REQUIRED"); required(r.sourceDescription(), "SOURCE_REQUIRED");
        if (r.direction() == null || !Set.of("INCREASE", "DECREASE", "MAINTAIN").contains(r.direction())) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DIRECTION");
        if (r.frequencyDays() == null || r.frequencyDays() <= 0) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FREQUENCY");
        if (r.unitOfMeasureId() == null || jdbc.queryForObject("SELECT COUNT(*) FROM unit_of_measure WHERE id=? AND active=TRUE", Long.class, r.unitOfMeasureId()) != 1)
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_UNIT");
        Long duplicates = jdbc.queryForObject("SELECT COUNT(*) FROM metric_definition WHERE LOWER(name)=LOWER(?) AND unit_of_measure_id=? AND id<>?",
                Long.class, r.name().trim(), r.unitOfMeasureId(), id == null ? -1 : id);
        if (duplicates != null && duplicates > 0) throw error(HttpStatus.CONFLICT, "DUPLICATE_NAME");
        Metric before = id == null ? null : findMetric(id);
        boolean active = r.active() == null || r.active();
        if (!active && id != null && jdbc.queryForObject("SELECT COUNT(*) FROM key_result WHERE metric_definition_id=? AND active=TRUE", Long.class, id) > 0)
            throw error(HttpStatus.CONFLICT, "ACTIVE_DEPENDENTS");
        Map<String, Object> values = new HashMap<>();
        values.put("name", r.name().trim()); values.put("unit_of_measure_id", r.unitOfMeasureId()); values.put("direction", r.direction());
        values.put("source_description", r.sourceDescription().trim()); values.put("frequency_days", r.frequencyDays());
        values.put("numerator_description", r.numeratorDescription()); values.put("denominator_description", r.denominatorDescription());
        values.put("inclusion_rules", r.inclusionRules()); values.put("exclusion_rules", r.exclusionRules()); values.put("active", active);
        if (id == null) {
            id = new SimpleJdbcInsert(jdbc).withTableName("metric_definition").usingColumns(values.keySet().toArray(String[]::new))
                    .usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
        } else if (r.version() == null || jdbc.update("UPDATE metric_definition SET name=?, unit_of_measure_id=?, direction=?, source_description=?, frequency_days=?, numerator_description=?, denominator_description=?, inclusion_rules=?, exclusion_rules=?, active=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?",
                r.name().trim(), r.unitOfMeasureId(), r.direction(), r.sourceDescription().trim(), r.frequencyDays(), r.numeratorDescription(), r.denominatorDescription(), r.inclusionRules(), r.exclusionRules(), active, id, r.version()) != 1)
            throw error(HttpStatus.CONFLICT, "VERSION_CONFLICT");
        Metric after = findMetric(id);
        audit.record(before == null ? AuditAction.CREATE : AuditAction.UPDATE, "METRIC_DEFINITION", id, "Ficha métrica: " + after.name(), before, after);
        return after;
    }
    private void duplicate(String table, String name, Long id) {
        // Table is an internal constant; user input is always bound as a parameter.
        if (jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE LOWER(name)=LOWER(?) AND id<>?", Long.class, name.trim(), id == null ? -1 : id) > 0)
            throw error(HttpStatus.CONFLICT, "DUPLICATE_NAME");
    }
    static void required(String value, String code) {
        if (value == null || value.isBlank() || value.length() > 10000) throw error(HttpStatus.UNPROCESSABLE_ENTITY, code);
    }
    static BusinessException error(HttpStatus status, String code) { return new BusinessException(status, code); }
}
