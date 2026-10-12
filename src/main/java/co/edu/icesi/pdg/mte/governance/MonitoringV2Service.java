package co.edu.icesi.pdg.mte.governance;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.*;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Service.*;

@Service
@Transactional
public class MonitoringV2Service {
    public record CutWrite(Long schoolId,Long cycleId,String cutCode,LocalDate cutDate) {}
    public record ConsolidateWrite(String idempotencyKey,String notes) {}
    public record SituationWrite(String title,String description,String severity,String escalationLevel) {}
    public record EntryWrite(Long departmentId,String observation,List<String> evidenceUrls,List<SituationWrite> situations) {}
    public record SituationTransition(String status,String comment,String evidenceUrl) {}
    public record CorrectiveWrite(Long responsibleId,LocalDate dueAt,String decisionDescription) {}
    public record CorrectiveTransition(String status,String comment,String evidenceUrl) {}
    public record DepartmentDelivery(Long departmentId,Long entryId,boolean confirmed) {}
    private final JdbcTemplate jdbc; private final TrackingV2Service tracking; private final PoliciesV2Service policies;
    private final ObjectMapper mapper; private final AuditService audit;
    public MonitoringV2Service(JdbcTemplate jdbc,TrackingV2Service tracking,PoliciesV2Service policies,ObjectMapper mapper,AuditService audit) { this.jdbc=jdbc;this.tracking=tracking;this.policies=policies;this.mapper=mapper;this.audit=audit; }
    private String roles(long actor) { return "r.professor_id="+actor+" AND r.started_at<=CURRENT_TIMESTAMP AND r.ended_at IS NULL"; }
    private String scope(TrackingV2Service.Actor actor) {
        if(actor.admin()) return "1=1";
        return "EXISTS(SELECT 1 FROM user_role r LEFT JOIN department rd ON rd.id=r.department_id WHERE "+roles(actor.id())+" AND (r.role_name IN ('RECTOR','AUDITOR') OR (r.role_name='DECANO' AND r.faculty_id=s.faculty_id) OR (r.role_name='DIRECTOR' AND r.school_id=s.id) OR (r.role_name IN ('JEFE','RESPONSABLE') AND rd.school_id=s.id)))";
    }
    private boolean director(long school,long actor) { return jdbc.queryForObject("SELECT COUNT(*) FROM user_role r WHERE "+roles(actor)+" AND r.role_name='DIRECTOR' AND r.school_id=?",Long.class,school)>0; }
    private boolean auditor(long actor) { return jdbc.queryForObject("SELECT COUNT(*) FROM user_role r WHERE "+roles(actor)+" AND r.role_name='AUDITOR'",Long.class)>0; }
    private boolean schoolReportReader(long school,TrackingV2Service.Actor actor) { return actor.admin() || auditor(actor.id()) || director(school,actor.id()) || jdbc.queryForObject("SELECT COUNT(*) FROM user_role r WHERE "+roles(actor.id())+" AND (r.role_name='RECTOR' OR (r.role_name='DECANO' AND r.faculty_id=(SELECT faculty_id FROM school WHERE id=?)))",Long.class,school)>0; }
    Map<String,Object> finalReportCut(long id,boolean write) { var row=cut(id,write);if(!schoolReportReader(((Number)row.get("schoolId")).longValue(),tracking.actor())) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");return row; }
    Map<String,Boolean> finalReportPermissions(Map<String,Object> cut) {
        boolean active=Boolean.TRUE.equals(cut.get("schoolActive"))&&Boolean.TRUE.equals(cut.get("facultyActive"));
        return Map.of("director",active&&canWrite(cut,"DIRECTOR",null),"rector",active&&canWrite(cut,"RECTOR",null));
    }
    Map<String,Object> rectorReportCut(long id) {
        lockCycleForCut(id);
        jdbc.queryForList("SELECT id FROM monitoring_cut WHERE id=? FOR UPDATE",id);
        var cut=finalReportCut(id,false);
        if(!canWrite(cut,"RECTOR",null))throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");
        if(!Boolean.TRUE.equals(cut.get("schoolActive"))||!Boolean.TRUE.equals(cut.get("facultyActive")))throw error(HttpStatus.CONFLICT,"INACTIVE_RESOURCE");
        return cut;
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> schools() {
        var actor=tracking.actor();
        var rows=tracking.jsonRows("SELECT s.id,s.name FROM school s JOIN faculty f ON f.id=s.faculty_id WHERE s.active=TRUE AND f.active=TRUE AND "+scope(actor)+" ORDER BY s.name,s.id");
        rows.forEach(s->s.put("canManage",director(((Number)s.get("id")).longValue(),actor.id()))); return rows;
    }
    private String cutQuery() { return "SELECT c.*,c.school_id AS \"schoolId\",s.name AS \"schoolName\",s.active AS \"schoolActive\",f.active AS \"facultyActive\",c.cycle_id AS \"cycleId\",cy.name AS \"cycleName\",c.cut_code AS \"cutCode\",c.cut_date AS \"cutDate\",c.snapshot_as_of AS \"asOf\",s.faculty_id AS \"facultyId\" FROM monitoring_cut c JOIN school s ON s.id=c.school_id LEFT JOIN faculty f ON f.id=s.faculty_id JOIN strategic_cycle cy ON cy.id=c.cycle_id"; }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> cuts() {
        var actor=tracking.actor();
        var rows=tracking.jsonRows("SELECT c.id,c.version,c.status,c.school_id AS \"schoolId\",s.name AS \"schoolName\",c.cycle_id AS \"cycleId\",cy.name AS \"cycleName\",c.cut_code AS \"cutCode\",c.cut_date AS \"cutDate\",c.snapshot_as_of AS \"asOf\" FROM monitoring_cut c JOIN school s ON s.id=c.school_id JOIN strategic_cycle cy ON cy.id=c.cycle_id WHERE "+scope(actor)+" ORDER BY c.cut_date DESC,c.id DESC LIMIT 100");
        rows.forEach(c->{c.put("canManage",director(((Number)c.get("schoolId")).longValue(),actor.id()));c.put("canReadFinalReports",schoolReportReader(((Number)c.get("schoolId")).longValue(),actor));}); return rows;
    }
    private Map<String,Object> cut(long id,boolean write) {
        var actor=tracking.actor();
        if(write)lockCycleForCut(id);
        if(write) jdbc.queryForList("SELECT id FROM monitoring_cut WHERE id=? FOR UPDATE",id);
        var rows=tracking.jsonRows(cutQuery()+" WHERE c.id=? AND "+scope(actor),id);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        var cut=rows.get(0);
        if(write && !director(((Number)cut.get("schoolId")).longValue(),actor.id())) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");
        if(write && (!Boolean.TRUE.equals(cut.get("schoolActive")) || !Boolean.TRUE.equals(cut.get("facultyActive")))) throw error(HttpStatus.CONFLICT,"INACTIVE_RESOURCE");
        return cut;
    }
    public Map<String,Object> create(CutWrite r) {
        var actor=tracking.actor(); required(r.cutCode(),"NAME_REQUIRED");
        if(r.cutCode().length()>50 || r.schoolId()==null || r.cycleId()==null || r.cutDate()==null) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        if(!director(r.schoolId(),actor.id())) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM school s JOIN faculty f ON f.id=s.faculty_id WHERE s.id=? AND s.active=TRUE AND f.active=TRUE",Long.class,r.schoolId())!=1) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM strategic_cycle WHERE id=? AND status IN ('DRAFT','ACTIVE') AND start_date<=? AND end_date>=?",Long.class,r.cycleId(),r.cutDate(),r.cutDate())!=1) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_CYCLE");
        tracking.lockOpenCycle(r.cycleId());
        long id=tracking.insert("monitoring_cut",Map.of("school_id",r.schoolId(),"cycle_id",r.cycleId(),"cut_code",r.cutCode().trim(),"cut_date",java.sql.Date.valueOf(r.cutDate())));
        var after=cut(id,false); audit.record(AuditAction.CREATE,"MONITORING_CUT",id,"Corte de Escuela creado",null,publicCut(after)); return publicCut(after);
    }
    private Map<String,Object> publicCut(Map<String,Object> cut) {
        var result=new LinkedHashMap<String,Object>(); for(String key:List.of("id","version","status","schoolId","schoolName","cycleId","cycleName","cutCode","cutDate","asOf")) if(cut.containsKey(key)) result.put(key,cut.get(key));
        result.put("canManage",director(((Number)cut.get("schoolId")).longValue(),tracking.actor().id()));result.put("canReadFinalReports",schoolReportReader(((Number)cut.get("schoolId")).longValue(),tracking.actor())); return result;
    }
    private void lockCycleForCut(long id){var rows=jdbc.queryForList("SELECT cycle_id FROM monitoring_cut WHERE id=?",id);if(!rows.isEmpty())tracking.lockOpenCycle(((Number)rows.get(0).get("cycle_id")).longValue());}
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> validate(long id,long version) {
        var before=cut(id,true); checkVersion(before,version);
        if(!Set.of("OPEN","VALIDATED").contains(before.get("status"))) throw error(HttpStatus.CONFLICT,"INVALID_TRANSITION");
        var policy=policies.current(true); if(policy==null) throw error(HttpStatus.CONFLICT,"POLICY_UNSET");
        long school=((Number)before.get("schoolId")).longValue(),cycle=((Number)before.get("cycleId")).longValue();
        String department="COALESCE(o.department_id,(SELECT p.department_id FROM kr_assignment a JOIN professor p ON p.id=a.professor_id WHERE a.key_result_id=k.id AND a.ended_at IS NULL LIMIT 1))";
        var inventory=tracking.jsonRows("SELECT k.id,k.name,k.version,k.status,k.active,d.id AS \"departmentId\",d.name AS \"departmentName\",m.name AS \"metricName\",u.name AS \"unitName\",k.base_value AS \"baseValue\",k.target_value AS \"targetValue\",k.metric_definition_id AS \"metricDefinitionId\",m.active AS \"metricActive\",u.active AS \"unitActive\",u.type AS \"unitType\",u.decimal_precision AS precision,k.strategic_cycle_id AS \"cycleId\",d.active AS \"departmentActive\",(SELECT COUNT(*) FROM kr_assignment a JOIN professor p ON p.id=a.professor_id WHERE a.key_result_id=k.id AND a.ended_at IS NULL AND p.active=TRUE AND p.department_id=d.id) AS owners,(SELECT COUNT(*) FROM kr_goal_link l JOIN goal g ON g.id=l.goal_id WHERE l.key_result_id=k.id AND l.ended_at IS NULL AND g.active=TRUE) AS goals,(SELECT COUNT(*) FROM kr_bet_link l JOIN strategic_bet b ON b.id=l.strategic_bet_id WHERE l.key_result_id=k.id AND l.ended_at IS NULL AND b.active=TRUE) AS bets,(SELECT measured_value FROM kr_measurement e WHERE e.key_result_id=k.id AND e.status='ACTIVE' AND e.observed_at<=CURRENT_TIMESTAMP AND e.registered_at<=CURRENT_TIMESTAMP ORDER BY observed_at DESC,id DESC LIMIT 1) AS \"measuredValue\",(SELECT p.name FROM kr_assignment a JOIN professor p ON p.id=a.professor_id WHERE a.key_result_id=k.id AND a.ended_at IS NULL ORDER BY a.id DESC LIMIT 1) AS responsible,(SELECT declared_by FROM kr_progress_entry e WHERE e.key_result_id=k.id AND e.declared_at<=CURRENT_TIMESTAMP ORDER BY declared_at DESC,id DESC LIMIT 1) AS \"declaredBy\",(SELECT declared_at FROM kr_progress_entry e WHERE e.key_result_id=k.id AND e.declared_at<=CURRENT_TIMESTAMP ORDER BY declared_at DESC,id DESC LIMIT 1) AS \"declaredAt\",(SELECT observed_at FROM kr_measurement e WHERE e.key_result_id=k.id AND e.status='ACTIVE' AND e.observed_at<=CURRENT_TIMESTAMP AND e.registered_at<=CURRENT_TIMESTAMP ORDER BY observed_at DESC,id DESC LIMIT 1) AS \"observedAt\",(SELECT registered_by FROM kr_measurement e WHERE e.key_result_id=k.id AND e.status='ACTIVE' AND e.observed_at<=CURRENT_TIMESTAMP AND e.registered_at<=CURRENT_TIMESTAMP ORDER BY observed_at DESC,id DESC LIMIT 1) AS \"observedBy\",(SELECT progress_percent FROM kr_progress_entry e WHERE e.key_result_id=k.id AND e.declared_at<=CURRENT_TIMESTAMP ORDER BY declared_at DESC,id DESC LIMIT 1) AS \"manualProgress\" FROM key_result k LEFT JOIN objective o ON o.id=k.objective_id JOIN department d ON d.id="+department+" LEFT JOIN metric_definition m ON m.id=k.metric_definition_id LEFT JOIN unit_of_measure u ON u.id=m.unit_of_measure_id WHERE d.school_id=? AND k.strategic_cycle_id=? ORDER BY k.id LIMIT 1001",school,cycle);
        var records=inventory.stream().filter(k->Boolean.TRUE.equals(k.get("active"))&&Set.of("ACTIVO","ACTIVE").contains(String.valueOf(k.get("status")))).toList();
        if(inventory.isEmpty()) throw error(HttpStatus.CONFLICT,"CUT_EMPTY"); if(inventory.size()>1000) throw error(HttpStatus.CONFLICT,"SNAPSHOT_TOO_LARGE");
        for(var kr:records) if(!Boolean.TRUE.equals(kr.get("metricActive")) || !Boolean.TRUE.equals(kr.get("unitActive")) || !Boolean.TRUE.equals(kr.get("departmentActive")) || !validValues(kr) || ((Number)kr.get("owners")).longValue()!=1 || ((Number)kr.get("goals")).longValue()<policy.minGoals() || ((Number)kr.get("bets")).longValue()<policy.minBets()) throw error(HttpStatus.CONFLICT,"CUT_NOT_READY");
        Instant asOf=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",Timestamp.class).toInstant();
        var snapshot=new LinkedHashMap<String,Object>();snapshot.put("keyResults",records);snapshot.put("policy",policy);snapshot.put("asOf",asOf.toString());snapshot.put("schemaVersion",3);snapshot.put("lifecycleInventory",inventory);
        snapshot.putAll(captureExecution(school,cycle));
        var participants=tracking.jsonRows("SELECT id,name FROM department WHERE school_id=? AND active=TRUE ORDER BY id",school);if(participants.isEmpty()) throw error(HttpStatus.CONFLICT,"CUT_NOT_READY");snapshot.put("departments",participants);
        jdbc.update("UPDATE monitoring_cut SET status='VALIDATED',version=version+1,snapshot=CAST(? AS JSONB),snapshot_as_of=?,validated_by=? WHERE id=?",json(snapshot),Timestamp.from(asOf),tracking.actor().id(),id);
        jdbc.update("INSERT INTO monitoring_cut_snapshot(cut_id,version,snapshot,as_of,actor_id) VALUES(?,?,CAST(? AS JSONB),?,?)",id,version+1,json(snapshot),Timestamp.from(asOf),tracking.actor().id());
        var after=publicCut(cut(id,false)); audit.record(AuditAction.UPDATE,"MONITORING_CUT",id,"Corte validado con snapshot",publicCut(before),snapshot); return after;
    }
    private void checkVersion(Map<String,Object> row,long version) { if(((Number)row.get("version")).longValue()!=version) throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT"); }
    private Map<String,Object> captureExecution(long school,long cycle) {
        var initiatives=tracking.jsonRows("SELECT i.id,i.title,i.status,i.version,i.key_result_id AS \"keyResultId\",i.department_id AS \"departmentId\",d.name AS \"departmentName\",(SELECT p.name FROM initiative_assignment a JOIN professor p ON p.id=a.professor_id WHERE a.initiative_id=i.id AND a.ended_at IS NULL ORDER BY a.id DESC LIMIT 1) AS responsible,(SELECT progress_percent FROM initiative_progress_entry e WHERE e.initiative_id=i.id AND e.occurred_at<=CURRENT_TIMESTAMP ORDER BY occurred_at DESC,id DESC LIMIT 1) AS \"manualProgress\",(SELECT occurred_at FROM initiative_progress_entry e WHERE e.initiative_id=i.id AND e.occurred_at<=CURRENT_TIMESTAMP ORDER BY occurred_at DESC,id DESC LIMIT 1) AS \"declaredAt\",(SELECT p.name FROM initiative_progress_entry e JOIN professor p ON p.id=e.actor_id WHERE e.initiative_id=i.id AND e.occurred_at<=CURRENT_TIMESTAMP ORDER BY occurred_at DESC,e.id DESC LIMIT 1) AS \"declaredBy\" FROM initiative i JOIN key_result k ON k.id=i.key_result_id JOIN department d ON d.id=i.department_id WHERE d.school_id=? AND k.strategic_cycle_id=? AND i.active=TRUE AND i.created_at<=CURRENT_TIMESTAMP ORDER BY i.id LIMIT 1001",school,cycle);
        var tasks=tracking.jsonRows("SELECT t.id,t.initiative_id AS \"initiativeId\",i.department_id AS \"departmentId\",t.external_task_id AS \"externalTaskId\",t.title_snapshot AS title,t.bucket_snapshot AS \"bucketName\",t.status_snapshot AS status,t.provider,t.last_synced_at AS \"lastSyncedAt\" FROM planner_task_ref t JOIN initiative i ON i.id=t.initiative_id JOIN key_result k ON k.id=i.key_result_id JOIN department d ON d.id=i.department_id WHERE d.school_id=? AND k.strategic_cycle_id=? AND i.active=TRUE AND t.last_synced_at<=CURRENT_TIMESTAMP ORDER BY t.id LIMIT 2001",school,cycle);
        if(initiatives.size()>1000 || tasks.size()>2000) throw error(HttpStatus.CONFLICT,"SNAPSHOT_TOO_LARGE");
        return Map.of("initiatives",initiatives,"tasks",tasks);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> report(long id,Long departmentId) {
        var cut=cut(id,false);if(cut.get("asOf")==null) throw error(HttpStatus.CONFLICT,"CUT_NOT_VALIDATED");
        Map<String,Object> snapshot;
        try { var tree=mapper.readTree(jdbc.queryForObject("SELECT CAST(snapshot AS VARCHAR) FROM monitoring_cut WHERE id=?",String.class,id));if(tree.isTextual()) tree=mapper.readTree(tree.asText());snapshot=mapper.convertValue(tree,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}); }
        catch(Exception e) { throw error(HttpStatus.CONFLICT,"SNAPSHOT_UNAVAILABLE"); }
        if(snapshot==null || !(snapshot.get("asOf") instanceof String) || !(snapshot.get("keyResults") instanceof List<?>) || !(snapshot.get("policy") instanceof Map<?,?>)) throw error(HttpStatus.CONFLICT,"SNAPSHOT_UNAVAILABLE");
        var actor=tracking.actor();boolean broad=actor.admin() || auditor(actor.id()) || director(((Number)cut.get("schoolId")).longValue(),actor.id()) || canWrite(cut,"DECANO",null) || canWrite(cut,"RECTOR",null);
        var departments=tracking.jsonRows("SELECT d.id,d.name FROM department d WHERE d.school_id=?"+(broad?"":" AND EXISTS(SELECT 1 FROM user_role r WHERE "+roles(actor.id())+" AND r.department_id=d.id)")+" ORDER BY d.name,d.id",cut.get("schoolId"));
        Set<Long> allowed=new HashSet<>();departments.forEach(d->allowed.add(((Number)d.get("id")).longValue()));
        if(departmentId!=null && !allowed.contains(departmentId)) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        var result=new LinkedHashMap<String,Object>();result.put("cut",publicCut(cut));result.put("asOf",snapshot.get("asOf"));result.put("policy",snapshot.get("policy"));result.put("departments",departments);result.put("departmentId",departmentId);
        result.put("captureVersion",jdbc.queryForObject("SELECT MAX(version) FROM monitoring_cut_snapshot WHERE cut_id=?",Long.class,id));
        for(String section:List.of("keyResults","lifecycleInventory","initiatives","tasks")) {
            result.put(section+"Captured",snapshot.containsKey(section));
            Object raw=snapshot.get(section);if(raw!=null && !(raw instanceof List<?>)) throw error(HttpStatus.CONFLICT,"SNAPSHOT_UNAVAILABLE");
            var visible=new ArrayList<Map<String,Object>>();
            if(raw instanceof List<?> records) for(Object record:records) {
                if(!(record instanceof Map<?,?>)) throw error(HttpStatus.CONFLICT,"SNAPSHOT_UNAVAILABLE");
                @SuppressWarnings("unchecked") var row=(Map<String,Object>)record;
                Long department=row.get("departmentId") instanceof Number n?n.longValue():null;
                if(department==null && (!broad || departmentId!=null)) throw error(HttpStatus.CONFLICT,"SNAPSHOT_SCOPE_UNAVAILABLE");
                if((broad || allowed.contains(department)) && (departmentId==null || departmentId.equals(department))) visible.add(row);
            }
            result.put(section,visible);
        }
        return result;
    }
    private boolean validValues(Map<String,Object> kr) {
        if(kr.get("baseValue")==null || kr.get("targetValue")==null || kr.get("precision")==null) return false;
        for(String field:List.of("baseValue","targetValue")) {
            BigDecimal value=new BigDecimal(kr.get(field).toString());
            if(value.stripTrailingZeros().scale()>Math.min(2,((Number)kr.get("precision")).intValue())) return false;
            if("PORCENTAJE".equals(kr.get("unitType")) && (value.signum()<0 || value.compareTo(BigDecimal.valueOf(100))>0)) return false;
            if("BOOLEANA".equals(kr.get("unitType")) && value.compareTo(BigDecimal.ZERO)!=0 && value.compareTo(BigDecimal.ONE)!=0) return false;
        }
        return true;
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public byte[] exportCsv(long id,Long departmentId) {
        var report=report(id,departmentId);var csv=new StringBuilder("as_of,capture_version,section,capture_state,id,parent_id,title,department_id,status,base_value,target_value,measured_value,manual_progress,responsible,provider,last_synced_at,active\r\n");
        for(String section:List.of("keyResults","lifecycleInventory","initiatives","tasks")) {
            @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)report.get(section);
            String state=!Boolean.TRUE.equals(report.get(section+"Captured"))?"NOT_CAPTURED":rows.isEmpty()?"EMPTY":"CAPTURED";
            if(rows.isEmpty()) csvRow(csv,report.get("asOf"),report.get("captureVersion"),section,state,null,null,null,null,null,null,null,null,null,null,null,null,null);
            for(var row:rows) csvRow(csv,report.get("asOf"),report.get("captureVersion"),section,state,row.get("id"),row.get(section.equals("tasks")?"initiativeId":"keyResultId"),row.get(Set.of("keyResults","lifecycleInventory").contains(section)?"name":"title"),row.get("departmentId"),row.get("status"),row.get("baseValue"),row.get("targetValue"),row.get("measuredValue"),row.get("manualProgress"),row.get("responsible"),row.get("provider"),row.get("lastSyncedAt"),row.get("active"));
        }
        byte[] bytes=csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash;try { hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        var filters=new LinkedHashMap<String,Object>();filters.put("departmentId",departmentId);filters.put("asOf",report.get("asOf"));filters.put("captureVersion",report.get("captureVersion"));
        @SuppressWarnings("unchecked") var cut=(Map<String,Object>)report.get("cut");
        long export=tracking.insert("report_export",Map.of("report_type","MONITORING_CUT","cut_id",id,"cycle_id",cut.get("cycleId"),"exported_by",Long.toString(tracking.actor().id()),"filters_applied",json(filters),"file_format","CSV","file_size_bytes",bytes.length,"file_sha256",hash));
        audit.record(AuditAction.CREATE,"REPORT_EXPORT",export,"CSV de captura autorizado",null,Map.of("cutId",id,"filters",filters,"sha256",hash,"sizeBytes",bytes.length));return bytes;
    }
    private void csvRow(StringBuilder csv,Object... values) {
        for(int i=0;i<values.length;i++) {
            if(i>0) csv.append(',');String value=values[i]==null?"":values[i].toString();
            // Spreadsheet applications must treat untrusted labels as text, including whitespace-prefixed formulas.
            String stripped=value.stripLeading();if(!(values[i] instanceof Number) && !stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0))>=0) value="'"+value;
            csv.append('"').append(value.replace("\"","\"\"")).append('"');
        }
        csv.append("\r\n");
    }
    public List<Map<String,Object>> consolidate(long id,long version,ConsolidateWrite r) {
        var cut=cut(id,true); if(r.idempotencyKey()==null || r.idempotencyKey().isBlank() || r.idempotencyKey().length()>128 || (r.notes()!=null && r.notes().length()>10000)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        if(cut.get("consolidation_key")!=null) {
            if(!r.idempotencyKey().equals(cut.get("consolidation_key")) || !Objects.equals(r.notes(),cut.get("consolidation_notes"))) throw error(HttpStatus.CONFLICT,"ALREADY_CONSOLIDATED");
            return parseList(jdbc.queryForObject("SELECT CAST(consolidation_response AS VARCHAR) FROM monitoring_cut WHERE id=?",String.class,id));
        }
        checkVersion(cut,version); if(!"VALIDATED".equals(cut.get("status")) || cut.get("asOf")==null) throw error(HttpStatus.CONFLICT,"CUT_NOT_VALIDATED");
        var policy=policies.current(true); if(policy==null) throw error(HttpStatus.CONFLICT,"POLICY_UNSET");
        List<Map<String,Object>> participants;
        try {
            var snapshot=mapper.readTree(jdbc.queryForObject("SELECT CAST(snapshot AS VARCHAR) FROM monitoring_cut WHERE id=?",String.class,id)); if(snapshot.isTextual()) snapshot=mapper.readTree(snapshot.asText());
            if(snapshot.path("policy").path("version").asInt(-1)!=policy.version()) throw error(HttpStatus.CONFLICT,"CUT_VALIDATION_STALE");
            if(!snapshot.path("departments").isArray() || snapshot.path("departments").isEmpty()) throw error(HttpStatus.CONFLICT,"DEPARTMENT_SCOPE_NOT_CAPTURED");
            participants=mapper.convertValue(snapshot.path("departments"),new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>(){});
        } catch(co.edu.icesi.pdg.mte.common.BusinessException e) { throw e; } catch(Exception e) { throw error(HttpStatus.CONFLICT,"CUT_NOT_VALIDATED"); }
        Timestamp asOf=Timestamp.from(Instant.parse(cut.get("asOf").toString()));
        for(String type:List.of("RECTOR","DECANO","DIRECTOR","DEPARTMENT")) tracking.insert("monitoring_branch",Map.of("cut_id",id,"branch_type",type,"as_of",asOf));
        long departmentBranch=jdbc.queryForObject("SELECT id FROM monitoring_branch WHERE cut_id=? AND branch_type='DEPARTMENT'",Long.class,id);
        for(var department:participants) tracking.insert("monitoring_department_result",Map.of("branch_id",departmentBranch,"department_id",department.get("id"),"department_name",department.get("name")));
        var branches=branches(id); jdbc.update("UPDATE monitoring_cut SET status='CONSOLIDATED',version=version+1,consolidation_key=?,consolidation_notes=?,consolidation_response=CAST(? AS JSONB) WHERE id=?",r.idempotencyKey(),r.notes(),json(branches),id);
        audit.record(AuditAction.STATUS_CHANGE,"MONITORING_CUT",id,"Cuatro ramas abiertas",publicCut(cut),branches); return branches;
    }
    private boolean canWrite(Map<String,Object> cut,String type,Long department) {
        long actor=tracking.actor().id(),school=((Number)cut.get("schoolId")).longValue();
        String role=switch(type) { case "RECTOR"->"RECTOR"; case "DECANO"->"DECANO"; case "DIRECTOR"->"DIRECTOR"; case "DEPARTMENT"->"JEFE"; default->throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST"); };
        String suffix=switch(type) { case "RECTOR"->""; case "DECANO"->" AND r.faculty_id="+cut.get("facultyId"); case "DIRECTOR"->" AND r.school_id="+school; default->" AND r.department_id IN (SELECT id FROM department WHERE school_id="+school+" AND active=TRUE)"+(department==null ? "" : " AND r.department_id="+department); };
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_role r WHERE "+roles(actor)+" AND r.role_name=?"+suffix,Long.class,role)>0;
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> branches(long cutId) {
        var cut=cut(cutId,false);
        var rows=tracking.jsonRows("SELECT b.id,b.cut_id AS \"cutId\",b.branch_type AS \"branchType\",b.status,b.as_of AS \"asOf\" FROM monitoring_branch b WHERE b.cut_id=? ORDER BY b.id",cutId);
        rows.forEach(b->b.put("canRecord",canWrite(cut,b.get("branchType").toString(),null))); return rows;
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> departments(long cutId) {
        var cut=cut(cutId,false); long actor=tracking.actor().id();
        return tracking.jsonRows("SELECT d.id,d.name FROM department d WHERE d.school_id=? AND d.active=TRUE AND EXISTS(SELECT 1 FROM user_role r WHERE "+roles(actor)+" AND r.role_name='JEFE' AND r.department_id=d.id) ORDER BY d.name",cut.get("schoolId"));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> departmentResults(long branchId) {
        var branch=branch(branchId,false);if(!"DEPARTMENT".equals(branch.get("branchType"))) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        var cut=cut(((Number)branch.get("cutId")).longValue(),false);var actor=tracking.actor();
        String filter=departmentReadFilter(cut,actor);
        var rows=tracking.jsonRows("SELECT x.id,x.department_id AS \"departmentId\",x.department_name AS \"departmentName\",x.version,x.entry_id AS \"entryId\",x.submitted_at AS \"submittedAt\",p.name AS \"submittedBy\",e.observation FROM monitoring_department_result x LEFT JOIN professor p ON p.id=x.submitted_by LEFT JOIN monitoring_entry e ON e.id=x.entry_id WHERE x.branch_id=?"+filter+" ORDER BY x.department_name,x.id",branchId);
        rows.forEach(row->{long department=((Number)row.get("departmentId")).longValue();row.put("canSubmit","OPEN".equals(branch.get("status")) && Boolean.TRUE.equals(cut.get("schoolActive")) && Boolean.TRUE.equals(cut.get("facultyActive")) && canWrite(cut,"DEPARTMENT",department));var latest=tracking.jsonRows("SELECT id,observation FROM monitoring_entry WHERE branch_id=? AND department_id=? ORDER BY id DESC LIMIT 1",branchId,department);row.put("latestEntryId",latest.isEmpty()?null:latest.get(0).get("id"));row.put("latestObservation",latest.isEmpty()?null:latest.get(0).get("observation"));});return rows;
    }
    private String departmentReadFilter(Map<String,Object> cut,TrackingV2Service.Actor actor) {
        boolean broad=canReadAllDepartments(cut,actor);
        return broad?"":" AND EXISTS(SELECT 1 FROM user_role r WHERE "+roles(actor.id())+" AND r.department_id=x.department_id)";
    }
    private boolean canReadAllDepartments(Map<String,Object> cut,TrackingV2Service.Actor actor) {
        return actor.admin() || auditor(actor.id()) || director(((Number)cut.get("schoolId")).longValue(),actor.id()) || canWrite(cut,"DECANO",null) || canWrite(cut,"RECTOR",null);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public TrackingV2Service.HistoryPage departmentDeliveryPage(long branchId,long departmentId,int page,int size) {
        if(departmentId<=0||page<0||page>1_000_000||size<1||size>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        var branch=branch(branchId,false);if(!"DEPARTMENT".equals(branch.get("branchType"))) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        var cut=cut(((Number)branch.get("cutId")).longValue(),false);
        var participants=jdbc.queryForList("SELECT x.id FROM monitoring_department_result x WHERE x.branch_id=? AND x.department_id=?"+departmentReadFilter(cut,tracking.actor()),Long.class,branchId,departmentId);
        if(participants.isEmpty()) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");long resultId=participants.get(0);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_department_delivery WHERE result_id=?",Long.class,resultId);
        var rows=tracking.jsonRows("SELECT h.id,h.version,h.entry_id AS \"entryId\",h.submitted_at AS \"submittedAt\",p.name AS \"deliveredBy\",CASE WHEN h.version=x.version THEN TRUE ELSE FALSE END AS \"current\",e.observation,e.actor_name AS \"entryActorName\",e.actor_role AS \"entryActorRole\",e.created_at AS \"entryCreatedAt\",e.evidence_urls AS evidence FROM monitoring_department_delivery h JOIN monitoring_department_result x ON x.id=h.result_id JOIN professor p ON p.id=h.actor_id JOIN monitoring_entry e ON e.id=h.entry_id WHERE h.result_id=? ORDER BY h.version DESC,h.id DESC LIMIT ? OFFSET ?",resultId,size,(long)page*size);
        rows.forEach(row->row.put("evidenceUrls",parseStrings(row.remove("evidence"))));
        return new TrackingV2Service.HistoryPage(rows,total,(total+size-1)/size,page,size);
    }
    public Map<String,Object> submitDepartment(long branchId,long version,DepartmentDelivery r) {
        var branch=branch(branchId,true);if(!"DEPARTMENT".equals(branch.get("branchType"))) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        var cut=cut(((Number)branch.get("cutId")).longValue(),false);
        if(r.departmentId()==null || r.entryId()==null) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        if(!canWrite(cut,"DEPARTMENT",r.departmentId())) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        if(!Boolean.TRUE.equals(cut.get("schoolActive")) || !Boolean.TRUE.equals(cut.get("facultyActive"))) throw error(HttpStatus.CONFLICT,"INACTIVE_RESOURCE");
        if(!"CONSOLIDATED".equals(cut.get("status")) || !"OPEN".equals(branch.get("status"))) throw error(HttpStatus.CONFLICT,"BRANCH_NOT_OPEN");
        var rows=tracking.jsonRows("SELECT id,version,entry_id AS \"entryId\" FROM monitoring_department_result WHERE branch_id=? AND department_id=?",branchId,r.departmentId());
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");var before=rows.get(0);checkVersion(before,version);
        if(!r.confirmed()) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"CONFIRMATION_REQUIRED");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_entry WHERE id=? AND branch_id=? AND department_id=?",Long.class,r.entryId(),branchId,r.departmentId())!=1) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        Long latest=jdbc.queryForObject("SELECT MAX(id) FROM monitoring_entry WHERE branch_id=? AND department_id=?",Long.class,branchId,r.departmentId());
        if(!Objects.equals(latest,r.entryId())) throw error(HttpStatus.CONFLICT,"DEPARTMENT_ENTRY_CHANGED");
        if(before.get("entryId")!=null && ((Number)before.get("entryId")).longValue()==r.entryId()) throw error(HttpStatus.CONFLICT,"DEPARTMENT_ALREADY_SUBMITTED");
        jdbc.update("UPDATE monitoring_department_result SET version=version+1,entry_id=?,submitted_by=?,submitted_at=CURRENT_TIMESTAMP WHERE id=?",r.entryId(),tracking.actor().id(),before.get("id"));
        tracking.insert("monitoring_department_delivery",Map.of("result_id",before.get("id"),"version",version+1,"entry_id",r.entryId(),"actor_id",tracking.actor().id()));
        if(jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_department_result WHERE branch_id=? AND entry_id IS NULL",Long.class,branchId)==0) jdbc.update("UPDATE monitoring_branch SET status='SUBMITTED',updated_at=CURRENT_TIMESTAMP WHERE id=?",branchId);
        var after=departmentResults(branchId).stream().filter(row->((Number)row.get("id")).longValue()==((Number)before.get("id")).longValue()).findFirst().orElseThrow();audit.record(AuditAction.STATUS_CHANGE,"MONITORING_DEPARTMENT_RESULT",((Number)before.get("id")).longValue(),"Resultado departamental entregado",before,after);return after;
    }
    private Map<String,Object> branch(long id,boolean write) {
        if(write){var cuts=jdbc.queryForList("SELECT cut_id FROM monitoring_branch WHERE id=?",id);if(!cuts.isEmpty())lockCycleForCut(((Number)cuts.get(0).get("cut_id")).longValue());}
        if(write) jdbc.queryForList("SELECT id FROM monitoring_branch WHERE id=? FOR UPDATE",id);
        var rows=tracking.jsonRows("SELECT id,cut_id AS \"cutId\",branch_type AS \"branchType\",status FROM monitoring_branch WHERE id=?",id);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE"); cut(((Number)rows.get(0).get("cutId")).longValue(),false); return rows.get(0);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> entries(long branchId) {
        var branch=branch(branchId,false); var cut=cut(((Number)branch.get("cutId")).longValue(),false); var actor=tracking.actor();
        boolean broad=actor.admin() || auditor(actor.id()) || director(((Number)cut.get("schoolId")).longValue(),actor.id()) || canWrite(cut,"DECANO",null) || canWrite(cut,"RECTOR",null);
        String filter=broad ? "" : " AND EXISTS(SELECT 1 FROM user_role r WHERE "+roles(actor.id())+" AND r.department_id=e.department_id)";
        var rows=tracking.jsonRows("SELECT e.id,e.branch_id AS \"branchId\",e.actor_name AS \"actorName\",e.actor_role AS \"actorRole\",e.observation,e.status_result AS \"statusResult\",e.evidence_urls AS evidence,e.created_at AS \"createdAt\",e.department_id AS \"departmentId\" FROM monitoring_entry e WHERE e.branch_id=?"+filter+" ORDER BY e.created_at DESC,e.id DESC LIMIT 100",branchId);
        rows.forEach(e->{ e.put("evidenceUrls",parseStrings(e.remove("evidence"))); e.put("situations",tracking.jsonRows("SELECT ms.id,ms.title,ms.description,ms.severity,ms.escalation_level AS \"escalationLevel\",g.status,g.version FROM monitoring_situation ms LEFT JOIN situation g ON g.id=ms.situation_id WHERE ms.entry_id=? ORDER BY ms.id",e.get("id"))); }); return rows;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public TrackingV2Service.HistoryPage entryPage(long branchId,int page,int size) {
        if(page<0||page>1_000_000||size<1||size>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        var branch=branch(branchId,false);var cut=cut(((Number)branch.get("cutId")).longValue(),false);var actor=tracking.actor();
        String filter=canReadAllDepartments(cut,actor)?"":" AND EXISTS(SELECT 1 FROM user_role r WHERE "+roles(actor.id())+" AND r.department_id=e.department_id)";
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_entry e WHERE e.branch_id=?"+filter,Long.class,branchId);
        var rows=tracking.jsonRows("SELECT e.id,e.branch_id AS \"branchId\",e.actor_name AS \"actorName\",e.actor_role AS \"actorRole\",e.observation,e.status_result AS \"statusResult\",e.evidence_urls AS evidence,e.created_at AS \"createdAt\",e.department_id AS \"departmentId\" FROM monitoring_entry e WHERE e.branch_id=?"+filter+" ORDER BY e.created_at DESC,e.id DESC LIMIT ? OFFSET ?",branchId,size,(long)page*size);
        rows.forEach(e->{ e.put("evidenceUrls",parseStrings(e.remove("evidence")));e.put("situations",tracking.jsonRows("SELECT ms.id,ms.title,ms.description,ms.severity,ms.escalation_level AS \"escalationLevel\",g.status,g.version FROM monitoring_situation ms LEFT JOIN situation g ON g.id=ms.situation_id WHERE ms.entry_id=? ORDER BY ms.id",e.get("id"))); });
        return new TrackingV2Service.HistoryPage(rows,total,(total+size-1)/size,page,size);
    }
    private Map<String,Object> scopedSituation(long id,boolean write) {
        var rows=tracking.jsonRows("SELECT ms.id,ms.situation_id AS \"situationId\",ms.escalation_level AS \"escalationLevel\",e.department_id AS \"departmentId\",b.branch_type AS \"branchType\",b.cut_id AS \"cutId\",g.version,g.status,g.title,g.description,g.severity,g.identified_at AS \"identifiedAt\",g.resolution_comment AS \"resolutionComment\",g.resolved_at AS \"resolvedAt\" FROM monitoring_situation ms JOIN situation g ON g.id=ms.situation_id JOIN monitoring_entry e ON e.id=ms.entry_id JOIN monitoring_branch b ON b.id=e.branch_id WHERE ms.id=? AND g.origin_type='MONITORING_ENTRY' AND g.origin_id=e.id",id);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        var row=rows.get(0); var cut=cut(((Number)row.get("cutId")).longValue(),false); var actor=tracking.actor();
        boolean broad=actor.admin() || auditor(actor.id()) || director(((Number)cut.get("schoolId")).longValue(),actor.id()) || canWrite(cut,"DECANO",null) || canWrite(cut,"RECTOR",null);
        if(!broad && (row.get("departmentId")==null || jdbc.queryForObject("SELECT COUNT(*) FROM user_role r WHERE "+roles(actor.id())+" AND r.department_id=?",Long.class,row.get("departmentId"))==0)) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        Long department=row.get("departmentId")==null ? null : ((Number)row.get("departmentId")).longValue();
        boolean manage=(!"DEPARTMENT".equals(row.get("branchType")) || department!=null) && (canWrite(cut,row.get("branchType").toString(),department) || (row.get("escalationLevel")!=null && canWrite(cut,row.get("escalationLevel").toString(),null)));
        row.put("canManage",manage && Boolean.TRUE.equals(cut.get("schoolActive")) && Boolean.TRUE.equals(cut.get("facultyActive")));
        if(write) {
            if(!manage) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");
            if(!Boolean.TRUE.equals(row.get("canManage"))) throw error(HttpStatus.CONFLICT,"INACTIVE_RESOURCE");
            // Serialize updates to the canonical finding; read again after acquiring the lock.
            jdbc.queryForList("SELECT id FROM situation WHERE id=? FOR UPDATE",row.get("situationId"));
            row.putAll(tracking.jsonRows("SELECT version,status FROM situation WHERE id=?",row.get("situationId")).get(0));
        }
        return row;
    }
    @Transactional(readOnly=true)
    public Map<String,Object> situation(long id) { return situation(id,false); }
    public Map<String,Object> situationForReport(long id) { return situation(id,true); }
    private Map<String,Object> situation(long id,boolean complete) {
        var row=scopedSituation(id,false);
        Long department=row.get("departmentId")==null ? null : ((Number)row.get("departmentId")).longValue();
        var cut=cut(((Number)row.get("cutId")).longValue(),false);
        row.put("canCreateCorrective","DEPARTMENT".equals(row.get("branchType")) && department!=null && canWrite(cut,"DEPARTMENT",department) && Boolean.TRUE.equals(cut.get("schoolActive")) && Boolean.TRUE.equals(cut.get("facultyActive")) && Set.of("OPEN","IN_PROGRESS").contains(row.get("status")));
        row.put("history",tracking.jsonRows(situationHistoryProjection()+" WHERE h.situation_id=? ORDER BY h.changed_at DESC,h.id DESC"+(complete?"":" LIMIT 100"),row.get("situationId")));
        return row;
    }
    private String situationHistoryProjection(){return "SELECT h.id,h.previous_status AS \"previousStatus\",h.new_status AS \"newStatus\",h.comment,h.changed_by AS \"changedBy\",p.name AS \"actorName\",h.changed_at AS \"changedAt\",h.evidence_id AS \"evidenceId\",e.external_ref AS \"evidenceUrl\" FROM situation_history h LEFT JOIN professor p ON CAST(p.id AS VARCHAR)=h.changed_by LEFT JOIN evidence e ON e.id=h.evidence_id AND e.situation_id=h.situation_id";}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public TrackingV2Service.HistoryPage situationHistoryPage(long id,int page,int size){
        var finding=scopedSituation(id,false);if(page<0 || size<1 || size>100)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM situation_history WHERE situation_id=?",Long.class,finding.get("situationId"));
        var rows=tracking.jsonRows(situationHistoryProjection()+" WHERE h.situation_id=? ORDER BY h.changed_at DESC,h.id DESC LIMIT ? OFFSET ?",finding.get("situationId"),size,(long)page*size);
        return new TrackingV2Service.HistoryPage(rows,total,(total+size-1)/size,page,size);
    }
    private void correctivePermission(Map<String,Object> finding) {
        if(!"DEPARTMENT".equals(finding.get("branchType")) || finding.get("departmentId")==null) throw error(HttpStatus.CONFLICT,"CORRECTIVE_ORIGIN_REQUIRED");
        var cut=cut(((Number)finding.get("cutId")).longValue(),false);
        if(!canWrite(cut,"DEPARTMENT",((Number)finding.get("departmentId")).longValue())) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");
        if(!Boolean.TRUE.equals(cut.get("schoolActive")) || !Boolean.TRUE.equals(cut.get("facultyActive"))) throw error(HttpStatus.CONFLICT,"INACTIVE_RESOURCE");
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> correctiveResponsibles(long id) {
        var finding=scopedSituation(id,false);correctivePermission(finding);
        return tracking.jsonRows("SELECT id,name FROM professor WHERE department_id=? AND active=TRUE ORDER BY name,id",finding.get("departmentId"));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> correctiveActions(long id) { return correctiveActions(id,false); }
    public List<Map<String,Object>> correctiveActionsForReport(long id) { return correctiveActions(id,true); }
    private List<Map<String,Object>> correctiveActions(long id,boolean complete) { return correctiveActions(id,complete,null); }
    @Transactional(readOnly=true)
    public Map<String,Object> correctiveAction(long id,long actionId) { var rows=correctiveActions(id,false,actionId);if(rows.isEmpty())throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");return rows.get(0); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public TrackingV2Service.HistoryPage correctivePage(long id,int page,int size){
        var finding=situation(id,false);if(page<0 || size<1 || size>100)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM corrective_action WHERE situation_id=?",Long.class,finding.get("situationId"));
        var rows=tracking.jsonRows(correctiveProjection()+" WHERE a.situation_id=? ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",finding.get("situationId"),size,(long)page*size);
        rows.forEach(row->row.put("canManage",Boolean.TRUE.equals(finding.get("canCreateCorrective"))));
        return new TrackingV2Service.HistoryPage(rows,total,(total+size-1)/size,page,size);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public TrackingV2Service.HistoryPage correctiveHistoryPage(long id,long actionId,int page,int size){
        var finding=scopedSituation(id,false);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM corrective_action WHERE id=? AND situation_id=?",Long.class,actionId,finding.get("situationId"))!=1)throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        if(page<0 || size<1 || size>100)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM corrective_action_history WHERE corrective_action_id=?",Long.class,actionId);
        var rows=tracking.jsonRows(correctiveHistoryProjection()+" WHERE h.corrective_action_id=? ORDER BY h.changed_at DESC,h.id DESC LIMIT ? OFFSET ?",actionId,size,(long)page*size);
        return new TrackingV2Service.HistoryPage(rows,total,(total+size-1)/size,page,size);
    }
    private List<Map<String,Object>> correctiveActions(long id,boolean complete,Long selected) {
        var finding=situation(id,complete);
        var rows=tracking.jsonRows(correctiveProjection()+" WHERE a.situation_id=?"+(selected==null?"":" AND a.id=?")+" ORDER BY a.created_at DESC,a.id DESC"+(complete||selected!=null?"":" LIMIT 100"),selected==null?new Object[]{finding.get("situationId")}:new Object[]{finding.get("situationId"),selected});
        rows.forEach(a->{a.put("canManage",Boolean.TRUE.equals(finding.get("canCreateCorrective")));a.put("history",tracking.jsonRows(correctiveHistoryProjection()+" WHERE h.corrective_action_id=? ORDER BY h.changed_at DESC,h.id DESC"+(complete?"":" LIMIT 100"),a.get("id")));});return rows;
    }
    private String correctiveProjection(){return "SELECT a.id,a.version,a.status,a.due_at AS \"dueAt\",a.decision_description AS \"decisionDescription\",a.assigned_to AS \"responsibleId\",p.name AS \"responsibleName\",a.created_at AS \"createdAt\",a.closed_at AS \"closedAt\" FROM corrective_action a LEFT JOIN professor p ON CAST(p.id AS VARCHAR)=a.assigned_to";}
    private String correctiveHistoryProjection(){return "SELECT h.id,h.previous_status AS \"previousStatus\",h.new_status AS \"newStatus\",h.comment,p.name AS \"actorName\",h.changed_at AS \"changedAt\",e.external_ref AS \"evidenceUrl\" FROM corrective_action_history h JOIN professor p ON p.id=h.actor_id LEFT JOIN evidence e ON e.id=h.evidence_id";}
    public Map<String,Object> createCorrective(long id,CorrectiveWrite r) {
        var finding=scopedSituation(id,true);correctivePermission(finding);
        if(!Set.of("OPEN","IN_PROGRESS").contains(finding.get("status"))) throw error(HttpStatus.CONFLICT,"INVALID_TRANSITION");
        required(r.decisionDescription(),"DESCRIPTION_REQUIRED");
        if(r.responsibleId()==null || r.dueAt()==null || r.dueAt().isBefore(LocalDate.now(ZoneId.of("America/Bogota")))) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_CORRECTIVE_ACTION");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM professor WHERE id=? AND department_id=? AND active=TRUE",Long.class,r.responsibleId(),finding.get("departmentId"))!=1) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        long action=tracking.insert("corrective_action",Map.of("situation_id",finding.get("situationId"),"department_id",finding.get("departmentId"),"assigned_to",r.responsibleId().toString(),"due_at",java.sql.Date.valueOf(r.dueAt()),"decision_description",r.decisionDescription().trim(),"created_by",Long.toString(tracking.actor().id())));
        var after=correctiveAction(id,action);audit.record(AuditAction.CREATE,"CORRECTIVE_ACTION",action,"Acción correctiva departamental",null,after);return after;
    }
    public Map<String,Object> transitionCorrective(long findingId,long actionId,long version,CorrectiveTransition r) {
        var finding=scopedSituation(findingId,true);correctivePermission(finding);
        var rows=tracking.jsonRows("SELECT id,version,status FROM corrective_action WHERE id=? AND situation_id=?",actionId,finding.get("situationId"));
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        // The finding lock serializes its actions and resolution in the same order.
        var before=rows.get(0);checkVersion(before,version);required(r.comment(),"COMMENT_REQUIRED");
        String current=before.get("status").toString();
        Set<String> next=switch(current) { case "PENDING"->Set.of("IN_PROGRESS","COMPLETED","CANCELLED"); case "IN_PROGRESS"->Set.of("COMPLETED","CANCELLED");default->Set.of(); };
        if(r.status()==null || !next.contains(r.status())) throw error(HttpStatus.CONFLICT,"INVALID_TRANSITION");
        Long evidence=resolutionEvidence(finding,r.evidenceUrl(),"COMPLETED".equals(r.status()));
        boolean closed=Set.of("COMPLETED","CANCELLED").contains(r.status());
        jdbc.update("UPDATE corrective_action SET status=?,version=version+1,evidence_id=?,closed_at="+(closed?"CURRENT_TIMESTAMP":"NULL")+" WHERE id=?",r.status(),evidence,actionId);
        var values=new HashMap<String,Object>();values.put("corrective_action_id",actionId);values.put("previous_status",current);values.put("new_status",r.status());values.put("comment",r.comment().trim());values.put("evidence_id",evidence);values.put("actor_id",tracking.actor().id());tracking.insert("corrective_action_history",values);
        var after=correctiveAction(findingId,actionId);audit.record(AuditAction.STATUS_CHANGE,"CORRECTIVE_ACTION",actionId,"Estado de acción correctiva",before,after);return after;
    }
    private Long resolutionEvidence(Map<String,Object> finding,String url,boolean required) {
        String ref=url==null ? "" : url.trim();
        if(required && ref.isBlank()) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"RESOLUTION_EVIDENCE_REQUIRED");
        if(ref.isBlank()) return null;
        try { var uri=java.net.URI.create(ref); if(ref.length()>1000 || !Set.of("http","https").contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT)) || uri.getHost()==null || uri.getRawUserInfo()!=null) throw new IllegalArgumentException(); } catch(IllegalArgumentException e) { throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EVIDENCE_URL"); }
        return tracking.insert("evidence",Map.of("situation_id",finding.get("situationId"),"title","Evidencia de seguimiento: "+finding.get("title").toString().substring(0,Math.min(200,finding.get("title").toString().length())),"evidence_type","URL","external_ref",ref,"created_by",Long.toString(tracking.actor().id())));
    }
    public Map<String,Object> transitionSituation(long id,long version,SituationTransition r) {
        var before=scopedSituation(id,true); checkVersion(before,version); required(r.comment(),"COMMENT_REQUIRED");
        String current=before.get("status").toString();
        Set<String> next=switch(current) { case "OPEN" -> Set.of("IN_PROGRESS","RESOLVED","CLOSED"); case "IN_PROGRESS" -> Set.of("RESOLVED","CLOSED"); case "RESOLVED" -> Set.of("IN_PROGRESS","CLOSED"); default -> Set.of(); };
        if(r.status()==null || !next.contains(r.status())) throw error(HttpStatus.CONFLICT,"INVALID_TRANSITION");
        boolean resolved=Set.of("RESOLVED","CLOSED").contains(r.status());
        if(resolved && jdbc.queryForObject("SELECT COUNT(*) FROM corrective_action WHERE situation_id=? AND status IN ('PENDING','IN_PROGRESS')",Long.class,before.get("situationId"))>0) throw error(HttpStatus.CONFLICT,"CORRECTIVE_ACTIONS_PENDING");
        Long evidence=resolutionEvidence(before,r.evidenceUrl(),resolved);
        jdbc.update("UPDATE situation SET status=?,version=version+1,resolution_comment=?,resolved_at="+(resolved?"CURRENT_TIMESTAMP":"NULL")+" WHERE id=?",r.status(),resolved?r.comment().trim():null,before.get("situationId"));
        var history=new HashMap<String,Object>(); history.put("situation_id",before.get("situationId"));history.put("previous_status",current);history.put("new_status",r.status());history.put("comment",r.comment().trim());history.put("evidence_id",evidence);history.put("changed_by",Long.toString(tracking.actor().id()));tracking.insert("situation_history",history);
        var after=situation(id); audit.record(AuditAction.STATUS_CHANGE,"SITUATION",((Number)before.get("situationId")).longValue(),"Seguimiento de situación de monitoreo",before,after); return after;
    }
    public Map<String,Object> record(long branchId,EntryWrite r,boolean review) {
        var before=branch(branchId,true); var cut=cut(((Number)before.get("cutId")).longValue(),false); String type=before.get("branchType").toString();
        if(!canWrite(cut,type,null)) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");
        if(type.equals("DEPARTMENT") && r.departmentId()!=null && !canWrite(cut,type,r.departmentId())) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        if(!"CONSOLIDATED".equals(cut.get("status"))) throw error(HttpStatus.CONFLICT,"CUT_NOT_VALIDATED");
        if(!Boolean.TRUE.equals(cut.get("schoolActive")) || !Boolean.TRUE.equals(cut.get("facultyActive"))) throw error(HttpStatus.CONFLICT,"INACTIVE_RESOURCE");
        if(!"OPEN".equals(before.get("status"))) throw error(HttpStatus.CONFLICT,"BRANCH_NOT_OPEN");
        if(type.equals("DEPARTMENT") && (r.departmentId()==null || review)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        if(type.equals("DEPARTMENT") && jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_department_result WHERE branch_id=? AND department_id=?",Long.class,branchId,r.departmentId())!=1) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        required(r.observation(),"COMMENT_REQUIRED");
        var urls=r.evidenceUrls()==null ? List.<String>of() : r.evidenceUrls(); if(urls.size()>20) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        for(String ref:urls) { try { var uri=java.net.URI.create(ref); if(ref.length()>1000 || !Set.of("http","https").contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT)) || uri.getHost()==null || uri.getRawUserInfo()!=null) throw new IllegalArgumentException(); } catch(IllegalArgumentException | NullPointerException e) { throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EVIDENCE_URL"); } }
        var situations=r.situations()==null ? List.<SituationWrite>of() : r.situations(); if(situations.size()>20) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        String escalation=type.equals("DIRECTOR") ? "DECANO" : type.equals("DECANO") ? "RECTOR" : null;
        for(var s:situations) { if(s==null) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST"); required(s.title(),"NAME_REQUIRED");required(s.description(),"DESCRIPTION_REQUIRED"); if(s.title().length()>255 || s.severity()==null || !Set.of("LOW","MEDIUM","HIGH","CRITICAL").contains(s.severity()) || (s.escalationLevel()!=null && !s.escalationLevel().equals(escalation))) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_SITUATION"); }
        var actor=tracking.actor(); var values=new HashMap<String,Object>(); values.put("branch_id",branchId); values.put("actor_email",jdbc.queryForObject("SELECT email FROM professor WHERE id=?",String.class,actor.id())); values.put("actor_name",actor.name()); values.put("actor_role",type.equals("DEPARTMENT") ? "JEFE" : type); values.put("observation",r.observation().trim()); values.put("status_result",situations.isEmpty() ? "NO_SITUATIONS" : "WITH_SITUATIONS"); values.put("evidence_urls",json(urls)); values.put("department_id",type.equals("DEPARTMENT") ? r.departmentId() : null);
        long entry=tracking.insert("monitoring_entry",values);
        for(var s:situations) {
            var generic=new HashMap<String,Object>(); generic.put("origin_type","MONITORING_ENTRY");generic.put("origin_id",entry);generic.put("department_id",values.get("department_id"));generic.put("cycle_id",cut.get("cycleId"));generic.put("severity",s.severity());generic.put("title",s.title().trim());generic.put("description",s.description().trim());generic.put("identified_by",Long.toString(actor.id()));
            long situation=tracking.insert("situation",generic); var detail=new HashMap<String,Object>();detail.put("entry_id",entry);detail.put("situation_id",situation);detail.put("title",s.title().trim());detail.put("description",s.description().trim());detail.put("severity",s.severity());detail.put("escalation_level",escalation);detail.put("escalated_to",escalation);tracking.insert("monitoring_situation",detail);
        }
        if(review) jdbc.update("UPDATE monitoring_branch SET status='REVIEWED',updated_at=CURRENT_TIMESTAMP WHERE id=?",branchId);
        Map<String,Object> after=Map.of("entryId",entry,"branchId",branchId,"observation",r.observation().trim(),"statusResult",situations.isEmpty() ? "NO_SITUATIONS" : "WITH_SITUATIONS");
        audit.record(AuditAction.CREATE,"MONITORING_ENTRY",entry,review ? "Revisión de rama registrada" : "Observación departamental registrada",before,after); return after;
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private List<String> parseStrings(Object value) { if(value==null) return List.of(); try { return mapper.readValue(value.toString(),new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){}); } catch(Exception e) { throw new IllegalStateException(e); } }
    private List<Map<String,Object>> parseList(String value) { try { var node=mapper.readTree(value); if(node.isTextual()) node=mapper.readTree(node.asText()); return mapper.convertValue(node,new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>(){}); } catch(Exception e) { throw new IllegalStateException(e); } }
}
