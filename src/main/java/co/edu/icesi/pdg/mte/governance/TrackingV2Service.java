package co.edu.icesi.pdg.mte.governance;

import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import co.edu.icesi.pdg.mte.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.math.BigDecimal;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Service.error;

@Service
@Transactional
public class TrackingV2Service {
    public record Actor(long id, long departmentId, String name, boolean admin, boolean manager) {}
    public record Kr(long id, String name, String unitName, String unitType, Long departmentId, String departmentName,
                     long version, boolean active, String status, BigDecimal measuredValue, BigDecimal progressPercent, String progressStatus) {}
    public record MeasurementWrite(BigDecimal measuredValue, Instant observedAt, String comment) {}
    public record ProgressWrite(BigDecimal progressPercent, String status, String comment, Long version) {}
    public record InitiativeWrite(Long keyResultId, Long departmentId, String title, String description, Long responsibleId) {}
    public record DeactivateWrite(String reason, Boolean confirmed, Long version) {}
    public record OwnerWrite(Long newProfessorId,String reason) {}
    public record HistoryPage(List<Map<String,Object>> items,long totalElements,long totalPages,int currentPage,int pageSize) {}
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public TrackingV2Service(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    Actor actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ExternalUserContext context)) throw error(HttpStatus.FORBIDDEN, "PROFILE_REQUIRED");
        var profile = jdbc.query("SELECT id, department_id, name FROM professor WHERE LOWER(email)=LOWER(?) AND active=TRUE",
                (rs, row) -> new Actor(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")),
                        auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"))), context.email());
        return profile.stream().findFirst().orElseThrow(() -> error(HttpStatus.FORBIDDEN, "PROFILE_REQUIRED"));
    }
    String scope(Actor actor) {
        return actor.admin() ? "1=1" : "(o.department_id=" + actor.departmentId() + " OR EXISTS (SELECT 1 FROM kr_assignment a JOIN professor p ON p.id=a.professor_id WHERE a.key_result_id=k.id AND a.ended_at IS NULL AND p.department_id=" + actor.departmentId() + "))";
    }
    @Transactional(readOnly = true)
    public List<Kr> keyResults() {
        Actor actor = actor();
        return jdbc.query(trackingProjection()+" WHERE "+scope(actor)+" ORDER BY k.name,k.id LIMIT 100",this::trackingRow);
    }
    @Transactional(readOnly = true)
    public Kr keyResultSummary(long id) {
        Actor actor=actor();
        return jdbc.query(trackingProjection()+" WHERE k.id=? AND "+scope(actor),this::trackingRow,id).stream().findFirst().orElseThrow(()->error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE"));
    }
    private String trackingProjection() {
        String department = "COALESCE(o.department_id,(SELECT p.department_id FROM kr_assignment a JOIN professor p ON p.id=a.professor_id WHERE a.key_result_id=k.id AND a.ended_at IS NULL LIMIT 1))";
        return "SELECT k.*, u.name AS unit_name, u.type AS unit_type, " + department + " AS dept_id, d.name AS dept_name, " +
                "(SELECT m.measured_value FROM kr_measurement m WHERE m.key_result_id=k.id AND m.status='ACTIVE' ORDER BY m.observed_at DESC, m.id DESC LIMIT 1) AS measured_value, " +
                "(SELECT p.progress_percent FROM kr_progress_entry p WHERE p.key_result_id=k.id ORDER BY p.declared_at DESC, p.id DESC LIMIT 1) AS manual_progress, " +
                "(SELECT p.status FROM kr_progress_entry p WHERE p.key_result_id=k.id ORDER BY p.declared_at DESC, p.id DESC LIMIT 1) AS manual_status " +
                "FROM key_result k JOIN unit_of_measure u ON u.id=k.unit_of_measure_id LEFT JOIN objective o ON o.id=k.objective_id LEFT JOIN department d ON d.id=" + department;
    }
    private Kr trackingRow(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        return new Kr(rs.getLong("id"),rs.getString("name"),rs.getString("unit_name"),rs.getString("unit_type"),rs.getObject("dept_id",Long.class),rs.getString("dept_name"),rs.getLong("version"),rs.getBoolean("active"),rs.getString("status"),rs.getBigDecimal("measured_value"),rs.getBigDecimal("manual_progress"),rs.getString("manual_status"));
    }
    Map<String, Object> scopedKr(long id, Actor actor, boolean write) {
        if(write){
            var cycles=jdbc.queryForList("SELECT k.strategic_cycle_id FROM key_result k LEFT JOIN objective o ON o.id=k.objective_id WHERE k.id=? AND "+scope(actor),id);
            if(!cycles.isEmpty()&&cycles.get(0).get("strategic_cycle_id")!=null)lockOpenCycle(((Number)cycles.get(0).get("strategic_cycle_id")).longValue());
            jdbc.queryForList("SELECT id FROM key_result WHERE id=? FOR UPDATE",id);
        }
        var rows = jdbc.queryForList("SELECT k.id, k.version, k.active, k.status, u.type AS unit_type, u.decimal_precision, COALESCE(o.department_id,(SELECT p.department_id FROM kr_assignment a JOIN professor p ON p.id=a.professor_id WHERE a.key_result_id=k.id AND a.ended_at IS NULL LIMIT 1)) AS department_id FROM key_result k JOIN unit_of_measure u ON u.id=k.unit_of_measure_id LEFT JOIN objective o ON o.id=k.objective_id WHERE k.id=? AND " + scope(actor), id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "OUT_OF_SCOPE");
        if (write) {
            if (!actor.admin() && !actor.manager() && jdbc.queryForObject("SELECT COUNT(*) FROM kr_assignment WHERE key_result_id=? AND professor_id=? AND ended_at IS NULL", Long.class, id, actor.id()) == 0)
                throw error(HttpStatus.FORBIDDEN, "FORBIDDEN");
            var row = rows.get(0);
            if (!Boolean.TRUE.equals(row.get("active")) || Set.of("CERRADO", "CLOSED").contains(String.valueOf(row.get("status")))) throw error(HttpStatus.CONFLICT, "INACTIVE_RESOURCE");
        }
        return rows.get(0);
    }
    void lockOpenCycle(long id){
        var cycles=jdbc.queryForList("SELECT status FROM strategic_cycle WHERE id=? FOR UPDATE",id);
        if(cycles.isEmpty())throw error(HttpStatus.NOT_FOUND,"CYCLE_NOT_FOUND");
        if("CLOSED".equals(cycles.get(0).get("status")))throw error(HttpStatus.CONFLICT,"CYCLE_CLOSED");
    }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> measurements(long id) {
        scopedKr(id, actor(), false);
        return jsonRows("SELECT id, key_result_id AS \"keyResultId\", measured_value AS \"measuredValue\", observed_at AS \"observedAt\", comment, registered_by AS \"registeredBy\", registered_at AS \"registeredAt\", status, version FROM kr_measurement WHERE key_result_id=? ORDER BY observed_at DESC, id DESC LIMIT 100", id);
    }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> progress(long id) {
        scopedKr(id, actor(), false);
        return jsonRows("SELECT id, key_result_id AS \"keyResultId\", progress_percent AS \"progressPercent\", status, comment, declared_by AS \"declaredBy\", declared_at AS \"declaredAt\" FROM kr_progress_entry WHERE key_result_id=? ORDER BY declared_at DESC, id DESC LIMIT 100", id);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public HistoryPage measurementPage(long id,int page,int size) { return historyPage(id,page,size,true); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public HistoryPage progressPage(long id,int page,int size) { return historyPage(id,page,size,false); }
    private HistoryPage historyPage(long id,int page,int size,boolean measurement) {
        if(page<0 || page>1000000 || size<1 || size>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        scopedKr(id,actor(),false);
        String table=measurement?"kr_measurement":"kr_progress_entry";
        String projection=measurement?"id, key_result_id AS \"keyResultId\", measured_value AS \"measuredValue\", observed_at AS \"observedAt\", comment, registered_by AS \"registeredBy\", registered_at AS \"registeredAt\", status, version":"id, key_result_id AS \"keyResultId\", progress_percent AS \"progressPercent\", status, comment, declared_by AS \"declaredBy\", declared_at AS \"declaredAt\"";
        String order=measurement?"observed_at":"declared_at";
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE key_result_id=?",Long.class,id);
        var items=jsonRows("SELECT "+projection+" FROM "+table+" WHERE key_result_id=? ORDER BY "+order+" DESC,id DESC LIMIT ? OFFSET ?",id,size,(long)page*size);
        return new HistoryPage(items,total,(total+size-1)/size,page,size);
    }
    public Map<String, Object> measure(long id, MeasurementWrite r) {
        Actor actor = actor(); var kr = scopedKr(id, actor, true);
        if (r.measuredValue() == null || r.observedAt() == null || r.observedAt().isAfter(Instant.now())) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_MEASUREMENT");
        String type = String.valueOf(kr.get("unit_type"));
        if (("PORCENTAJE".equals(type) && (r.measuredValue().signum() < 0 || r.measuredValue().compareTo(BigDecimal.valueOf(100)) > 0)) ||
                ("BOOLEANA".equals(type) && r.measuredValue().compareTo(BigDecimal.ZERO) != 0 && r.measuredValue().compareTo(BigDecimal.ONE) != 0) ||
                r.measuredValue().stripTrailingZeros().scale() > Math.min(4, ((Number) kr.get("decimal_precision")).intValue()) || r.measuredValue().abs().compareTo(new BigDecimal("99999999.9999")) > 0)
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_MEASUREMENT");
        Map<String, Object> values = new HashMap<>(); values.put("key_result_id", id); values.put("measured_value", r.measuredValue());
        values.put("observed_at", Timestamp.from(r.observedAt())); values.put("comment", r.comment()); values.put("registered_by", actor.name());
        long entry = insert("kr_measurement", values);
        var after = jsonRows("SELECT id, key_result_id AS \"keyResultId\", measured_value AS \"measuredValue\", observed_at AS \"observedAt\", comment, registered_by AS \"registeredBy\", registered_at AS \"registeredAt\", status, version FROM kr_measurement WHERE id=?", entry).get(0);
        audit.record(AuditAction.CREATE, "KR_MEASUREMENT", entry, "Medición de KR #" + id, null, after);
        // AD-4: measurements never recalculate or declare the manual percentage.
        return after;
    }
    public Map<String, Object> declareProgress(long id, ProgressWrite r) {
        Actor actor = actor(); scopedKr(id, actor, true); validateProgress(r);
        if (r.version() == null || jdbc.update("UPDATE key_result SET version=version+1 WHERE id=? AND version=?", id, r.version()) != 1) throw error(HttpStatus.CONFLICT, "VERSION_CONFLICT");
        Map<String, Object> values = new HashMap<>(); values.put("key_result_id", id); values.put("progress_percent", r.progressPercent());
        values.put("status", r.status()); values.put("comment", r.comment()); values.put("declared_by", actor.name());
        long entry = insert("kr_progress_entry", values);
        var after = progress(id).stream().filter(m -> ((Number)m.get("id")).longValue() == entry).findFirst().orElseThrow();
        audit.record(AuditAction.PROGRESS_REGISTERED, "KEY_RESULT", id, "Avance manual declarado", null, after);
        return after;
    }
    static void validateProgress(ProgressWrite r) {
        if (r.progressPercent() == null || r.progressPercent().signum() < 0 || r.progressPercent().compareTo(BigDecimal.valueOf(100)) > 0 || r.progressPercent().stripTrailingZeros().scale() > 2)
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PROGRESS");
        if (r.status() == null || !Set.of("ON_TRACK", "AT_RISK", "CRITICAL", "COMPLETED").contains(r.status())) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STATUS");
    }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> responsibles() {
        Actor a = actor();
        return jsonRows("SELECT p.id, p.name, p.department_id AS \"departmentId\", d.name AS \"departmentName\" FROM professor p JOIN department d ON d.id=p.department_id WHERE p.active=TRUE" + (a.admin() ? "" : " AND p.department_id=" + a.departmentId()) + " ORDER BY p.name LIMIT 100");
    }
    private String initiativeQuery() {
        return "SELECT i.id, i.key_result_id AS \"keyResultId\", k.name AS \"keyResultName\", i.department_id AS \"departmentId\", d.name AS \"departmentName\", i.title, i.description, i.status, i.progress_percent AS \"progressPercent\", i.version, i.active, p.id AS \"responsibleId\", p.name AS \"responsibleName\", i.created_at AS \"createdAt\" FROM initiative i JOIN key_result k ON k.id=i.key_result_id JOIN department d ON d.id=i.department_id LEFT JOIN initiative_assignment ia ON ia.initiative_id=i.id AND ia.ended_at IS NULL LEFT JOIN professor p ON p.id=ia.professor_id";
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public HistoryPage initiativePage(String text,String status,int page,int size) {
        if(page<0 || page>1000000 || size<1 || size>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        if(text!=null && text.length()>255) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        if(status!=null && !Set.of("DRAFT","ACTIVE","CLOSED","INACTIVE").contains(status)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_STATUS");
        Actor a=actor();String where=" WHERE "+(a.admin()?"1=1":"i.department_id=?");var params=new ArrayList<Object>();if(!a.admin())params.add(a.departmentId());
        if(text!=null && !text.isBlank()){where+=" AND LOWER(i.title) LIKE ? ESCAPE '!'";params.add("%"+text.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
        if(status!=null){where+=" AND i.status=?";params.add(status);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM initiative i"+where,Long.class,params.toArray());params.add(size);params.add((long)page*size);
        var items=jsonRows(initiativeQuery()+where+" ORDER BY i.created_at DESC,i.id DESC LIMIT ? OFFSET ?",params.toArray());
        return new HistoryPage(items,total,(total+size-1)/size,page,size);
    }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> initiatives() {
        Actor a = actor();
        return jsonRows(initiativeQuery() + (a.admin() ? "" : " WHERE i.department_id=" + a.departmentId()) + " ORDER BY i.created_at DESC, i.id DESC LIMIT 100");
    }
    @Transactional(readOnly = true)
    public Map<String, Object> getInitiative(long id) {
        return initiative(id, actor(), false);
    }
    Map<String, Object> initiative(long id, Actor a, boolean write) {
        if(write){var parents=jdbc.queryForList("SELECT i.key_result_id FROM initiative i WHERE i.id=?"+(a.admin()?"":" AND i.department_id="+a.departmentId()),id);if(parents.isEmpty())throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
            long parent=((Number)parents.get(0).get("key_result_id")).longValue();
            // Parent writability and locks; the initiative's own responsible check remains below.
            scopedKr(parent,new Actor(a.id(),a.departmentId(),a.name(),a.admin(),true),true);
        }
        if (write) jdbc.queryForList("SELECT id FROM initiative WHERE id=? FOR UPDATE", id);
        var rows = jsonRows(initiativeQuery() + " WHERE i.id=?" + (a.admin() ? "" : " AND i.department_id=" + a.departmentId()), id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "OUT_OF_SCOPE");
        var r = rows.get(0);
        if (write) {
            if (!a.admin() && !a.manager() && (r.get("responsibleId") == null || ((Number)r.get("responsibleId")).longValue() != a.id())) throw error(HttpStatus.FORBIDDEN, "FORBIDDEN");
            if (!Boolean.TRUE.equals(r.get("active")) || "CLOSED".equals(r.get("status"))) throw error(HttpStatus.CONFLICT, "INACTIVE_RESOURCE");
        }
        return r;
    }
    public Map<String, Object> createInitiative(long krId, InitiativeWrite r) {
        Actor a = actor();
        if (!a.admin() && !a.manager()) throw error(HttpStatus.FORBIDDEN, "FORBIDDEN");
        var kr = scopedKr(krId, a, true);
        GovernanceV2Service.required(r.title(), "NAME_REQUIRED"); GovernanceV2Service.required(r.description(), "DESCRIPTION_REQUIRED");
        if (r.title().length() > 255) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REQUEST");
        if (r.departmentId() == null || (!a.admin() && r.departmentId() != a.departmentId()) ||
                (kr.get("department_id") != null && ((Number)kr.get("department_id")).longValue() != r.departmentId())) throw error(HttpStatus.NOT_FOUND, "OUT_OF_SCOPE");
        if (r.responsibleId() == null || jdbc.queryForObject("SELECT COUNT(*) FROM professor WHERE id=? AND department_id=? AND active=TRUE", Long.class, r.responsibleId(), r.departmentId()) != 1) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RESPONSIBLE");
        long id = insert("initiative", Map.of("key_result_id", krId, "department_id", r.departmentId(), "title", r.title().trim(), "description", r.description().trim(), "progress_percent", BigDecimal.ZERO));
        insert("initiative_assignment", Map.of("initiative_id", id, "professor_id", r.responsibleId(), "actor_id", a.id()));
        var after = initiative(id, a, false);
        audit.record(AuditAction.CREATE, "INITIATIVE", id, "Iniciativa: " + r.title().trim(), null, after);
        return after;
    }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> initiativeProgress(long id) {
        initiative(id, actor(), false);
        return jsonRows("SELECT e.id, e.progress_percent AS \"progressPercent\", e.status, e.comment, e.occurred_at AS \"occurredAt\", p.name AS \"actorName\" FROM initiative_progress_entry e JOIN professor p ON p.id=e.actor_id WHERE e.initiative_id=? ORDER BY e.occurred_at DESC, e.id DESC LIMIT 100", id);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public HistoryPage initiativeProgressPage(long id,int page,int size) {
        if(page<0 || page>1000000 || size<1 || size>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        initiative(id,actor(),false);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM initiative_progress_entry WHERE initiative_id=?",Long.class,id);
        var items=jsonRows("SELECT e.id, e.progress_percent AS \"progressPercent\", e.status, e.comment, e.occurred_at AS \"occurredAt\", p.name AS \"actorName\" FROM initiative_progress_entry e JOIN professor p ON p.id=e.actor_id WHERE e.initiative_id=? ORDER BY e.occurred_at DESC,e.id DESC LIMIT ? OFFSET ?",id,size,(long)page*size);
        return new HistoryPage(items,total,(total+size-1)/size,page,size);
    }
    public Map<String, Object> declareInitiative(long id, ProgressWrite r) {
        Actor a = actor(); var before = initiative(id, a, true); validateProgress(r);
        GovernanceV2Service.required(r.comment(), "COMMENT_REQUIRED");
        // Operational lifecycle and qualitative status are separate from the KR's lifecycle.
        if (r.version() == null || jdbc.update("UPDATE initiative SET progress_percent=?, status='ACTIVE', version=version+1 WHERE id=? AND version=?", r.progressPercent(), id, r.version()) != 1) throw error(HttpStatus.CONFLICT, "VERSION_CONFLICT");
        insert("initiative_progress_entry", Map.of("initiative_id", id, "progress_percent", r.progressPercent(), "status", r.status(), "comment", r.comment().trim(), "actor_id", a.id()));
        var after = initiative(id, a, false);
        audit.record(AuditAction.PROGRESS_REGISTERED, "INITIATIVE", id, "Avance manual de iniciativa", before, after);
        return after;
    }
    public Map<String, Object> deactivate(long id, DeactivateWrite r) {
        Actor a = actor(); if (!a.admin() && !a.manager()) throw error(HttpStatus.FORBIDDEN, "FORBIDDEN");
        var before = initiative(id, a, true); GovernanceV2Service.required(r.reason(), "REASON_REQUIRED");
        if (!Boolean.TRUE.equals(r.confirmed())) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "CONFIRMATION_REQUIRED");
        if (r.version() == null || jdbc.update("UPDATE initiative SET active=FALSE, status='INACTIVE', version=version+1 WHERE id=? AND version=?", id, r.version()) != 1) throw error(HttpStatus.CONFLICT, "VERSION_CONFLICT");
        var after = initiative(id, a, false);
        audit.record(AuditAction.STATUS_CHANGE, "INITIATIVE", id, "Desactivada: " + r.reason().trim(), before, after);
        return after;
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> initiativeAssignments(long id) {
        initiative(id,actor(),false);
        return jsonRows("SELECT a.id,p.id AS \"professorId\",p.name,a.started_at AS \"startedAt\",a.ended_at AS \"endedAt\",actor.name AS \"actorName\" FROM initiative_assignment a JOIN professor p ON p.id=a.professor_id JOIN professor actor ON actor.id=a.actor_id WHERE a.initiative_id=? ORDER BY a.started_at DESC,a.id DESC LIMIT 100",id);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public HistoryPage initiativeAssignmentPage(long id,int page,int size) {
        if(page<0||page>1_000_000||size<1||size>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        initiative(id,actor(),false);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM initiative_assignment WHERE initiative_id=?",Long.class,id);
        var items=jsonRows("SELECT a.id,p.id AS \"professorId\",p.name,a.started_at AS \"startedAt\",a.ended_at AS \"endedAt\",actor.name AS \"actorName\" FROM initiative_assignment a JOIN professor p ON p.id=a.professor_id JOIN professor actor ON actor.id=a.actor_id WHERE a.initiative_id=? ORDER BY a.started_at DESC,a.id DESC LIMIT ? OFFSET ?",id,size,(long)page*size);
        return new HistoryPage(items,total,(total+size-1)/size,page,size);
    }
    public Map<String,Object> reassignInitiative(long id,OwnerWrite r,long version) {
        Actor a=actor(); if(!a.admin() && !a.manager()) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");
        var before=initiative(id,a,true); GovernanceV2Service.required(r.reason(),"REASON_REQUIRED");
        if(((Number)before.get("version")).longValue()!=version) throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        if(r.newProfessorId()==null) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_RESPONSIBLE");
        var candidate=jdbc.queryForList("SELECT p.department_id FROM professor p JOIN department d ON d.id=p.department_id WHERE p.id=? AND p.active=TRUE AND d.active=TRUE",r.newProfessorId());
        if(candidate.isEmpty()) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_RESPONSIBLE");
        if(((Number)candidate.get(0).get("department_id")).longValue()!=((Number)before.get("departmentId")).longValue()) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        var previous=jdbc.query("SELECT professor_id,started_at FROM initiative_assignment WHERE initiative_id=? AND ended_at IS NULL",(rs,row)->Map.of("id",rs.getLong(1),"start",rs.getTimestamp(2).toInstant()),id);
        if(!previous.isEmpty() && ((Number)previous.get(0).get("id")).longValue()==r.newProfessorId()) throw error(HttpStatus.CONFLICT,"SAME_RESPONSIBLE");
        Instant end=Instant.now(); for(var old:previous) { Instant start=(Instant)old.get("start"); if(!end.isAfter(start)) end=start.plusMillis(1); }
        jdbc.update("UPDATE initiative_assignment SET ended_at=? WHERE initiative_id=? AND ended_at IS NULL",Timestamp.from(end),id);
        insert("initiative_assignment",Map.of("initiative_id",id,"professor_id",r.newProfessorId(),"actor_id",a.id(),"started_at",Timestamp.from(end)));
        for(var old:previous) insert("assignment_history",Map.of("entity_type","INITIATIVE","entity_id",id,"professor_id",old.get("id"),"action","UNASSIGNED","actor_id",a.id()));
        insert("assignment_history",Map.of("entity_type","INITIATIVE","entity_id",id,"professor_id",r.newProfessorId(),"action","ASSIGNED","actor_id",a.id()));
        if(jdbc.update("UPDATE initiative SET version=version+1 WHERE id=? AND version=?",id,version)!=1) throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        var after=initiative(id,a,false); audit.record(AuditAction.UPDATE,"INITIATIVE",id,"Responsable reasignado: "+r.reason().trim(),before,after); return after;
    }
    long insert(String table, Map<String, Object> values) {
        var insert=new SimpleJdbcInsert(jdbc).withTableName(table);
        String schema=jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)java.sql.Connection::getSchema);
        if(schema!=null&&!schema.isBlank())insert.withSchemaName(schema);
        return insert.usingColumns(values.keySet().toArray(String[]::new)).usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
    }
    List<Map<String, Object>> jsonRows(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>();
            row.forEach((key, value) -> result.put(key.equals(key.toUpperCase(Locale.ROOT)) ? key.toLowerCase(Locale.ROOT) : key,
                    value instanceof Timestamp t ? t.toInstant().toString() : value instanceof java.sql.Date d ? d.toLocalDate().toString() : value instanceof java.time.OffsetDateTime t ? t.toInstant().toString() : value));
            return result;
        }).toList();
    }
}
