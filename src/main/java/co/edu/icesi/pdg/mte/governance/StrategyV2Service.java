package co.edu.icesi.pdg.mte.governance;

import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Service.*;

@Service
@Transactional
public class StrategyV2Service {
    public record Create(String name, String description, Long metricDefinitionId, BigDecimal baseValue,
                         BigDecimal targetValue, Long responsibleId, Long strategicCycleId, String type, List<Long> goalIds, List<Long> betIds) {}
    public record Revision(Long metricDefinitionId, BigDecimal baseValue, BigDecimal targetValue, String reason) {}
    public record LinkWrite(Long targetId,String reason) {}
    public record OwnerWrite(Long newProfessorId,String reason) {}
    public record TypeWrite(String newType,String reason) {}
    public record DeactivateWrite(String reason,Boolean confirmed) {}
    public record ActivationWrite(String reason) {}
    public record StatusWrite(String newStatus,String reason,Boolean confirmed,Long reportId) {
        public StatusWrite(String newStatus,String reason,Boolean confirmed){this(newStatus,reason,confirmed,null);}
    }
    public record Check(String code,boolean passed,long actual,Long required) {}
    public record ActivationCheck(boolean ready,Integer policyVersion,List<Check> checks) {}
    public record Page(List<Map<String,Object>> items, long totalElements, long totalPages, int currentPage, int pageSize) {}
    private final JdbcTemplate jdbc;
    private final TrackingV2Service tracking;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final PoliciesV2Service policies;
    public StrategyV2Service(JdbcTemplate jdbc, TrackingV2Service tracking, AuditService audit, ObjectMapper mapper,PoliciesV2Service policies) {
        this.jdbc=jdbc; this.tracking=tracking; this.audit=audit; this.mapper=mapper; this.policies=policies;
    }
    private void requireManager(TrackingV2Service.Actor a) { if (!a.admin() && !a.manager()) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN"); }
    @Transactional(readOnly=true)
    public Map<String,Object> choices() {
        tracking.actor();
        return Map.of("goals", tracking.jsonRows("SELECT id,name FROM goal WHERE active=TRUE ORDER BY name,id"),
                "bets", tracking.jsonRows("SELECT id,name FROM strategic_bet WHERE active=TRUE ORDER BY name,id"));
    }
    @Transactional(readOnly=true)
    public Page search(String text, Long cycleId, String status, Long responsibleId, Long goalId, Long betId, int page, int size) {
        if(page<0 || size<1 || size>100 || page>1000000) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        var a=tracking.actor(); var args=new ArrayList<Object>(); var where=new StringBuilder(tracking.scope(a));
        if(text!=null && !text.isBlank()) { where.append(" AND LOWER(k.name) LIKE ?"); args.add("%"+text.toLowerCase(Locale.ROOT).trim()+"%"); }
        if(cycleId!=null) { where.append(" AND k.strategic_cycle_id=?"); args.add(cycleId); }
        if(status!=null && !status.isBlank()) {
            if(!Set.of("DRAFT","ACTIVE","CLOSED","INACTIVE").contains(status)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_STATUS");
            if("INACTIVE".equals(status)) where.append(" AND k.active=FALSE");
            else { where.append(" AND k.active=TRUE AND k.status=?"); args.add(storageStatus(status)); }
        }
        if(responsibleId!=null) { where.append(" AND EXISTS(SELECT 1 FROM kr_assignment a WHERE a.key_result_id=k.id AND a.professor_id=? AND a.ended_at IS NULL)"); args.add(responsibleId); }
        if(goalId!=null) { where.append(" AND EXISTS(SELECT 1 FROM kr_goal_link l WHERE l.key_result_id=k.id AND l.goal_id=? AND l.ended_at IS NULL)"); args.add(goalId); }
        if(betId!=null) { where.append(" AND EXISTS(SELECT 1 FROM kr_bet_link l WHERE l.key_result_id=k.id AND l.strategic_bet_id=? AND l.ended_at IS NULL)"); args.add(betId); }
        String from=" FROM key_result k LEFT JOIN objective o ON o.id=k.objective_id WHERE "+where;
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());
        args.add(size); args.add((long)page*size);
        var ids=jdbc.queryForList("SELECT k.id"+from+" ORDER BY k.created_at DESC,k.id DESC LIMIT ? OFFSET ?",Long.class,args.toArray());
        return new Page(ids.stream().map(this::get).toList(),total,(total+size-1)/size,page,size);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> get(long id) {
        tracking.scopedKr(id,tracking.actor(),false);
        return definition(id);
    }
    Map<String,Object> definition(long id) {
        var data=tracking.jsonRows("SELECT k.id,k.name,k.description,k.metric_definition_id AS \"metricDefinitionId\", m.name AS \"metricDefinitionName\", k.base_value AS \"baseValue\", k.target_value AS \"targetValue\", k.strategic_cycle_id AS \"strategicCycleId\", c.name AS \"strategicCycleName\", k.status,k.type,k.version,k.active,k.objective_id AS \"legacyObjectiveId\", p.id AS \"responsibleId\", p.name AS \"responsibleName\", p.department_id AS \"departmentId\", (SELECT e.measured_value FROM kr_measurement e WHERE e.key_result_id=k.id AND e.status='ACTIVE' ORDER BY e.observed_at DESC,e.id DESC LIMIT 1) AS \"currentValue\", (SELECT e.progress_percent FROM kr_progress_entry e WHERE e.key_result_id=k.id ORDER BY e.declared_at DESC,e.id DESC LIMIT 1) AS \"progressPercentage\" FROM key_result k LEFT JOIN metric_definition m ON m.id=k.metric_definition_id LEFT JOIN strategic_cycle c ON c.id=k.strategic_cycle_id LEFT JOIN kr_assignment a ON a.key_result_id=k.id AND a.ended_at IS NULL LEFT JOIN professor p ON p.id=a.professor_id WHERE k.id=?",id).get(0);
        data.put("status",Boolean.TRUE.equals(data.get("active")) ? publicStatus(String.valueOf(data.get("status"))) : "INACTIVE");
        data.put("linkedGoalIds",jdbc.queryForList("SELECT goal_id FROM kr_goal_link WHERE key_result_id=? AND ended_at IS NULL ORDER BY goal_id",Long.class,id));
        data.put("linkedBetIds",jdbc.queryForList("SELECT strategic_bet_id FROM kr_bet_link WHERE key_result_id=? AND ended_at IS NULL ORDER BY strategic_bet_id",Long.class,id));
        return data;
    }
    public Map<String,Object> create(Create r,String key) {
        var actor=tracking.actor(); requireManager(actor);
        if(key==null || key.isBlank() || key.length()>128) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"IDEMPOTENCY_KEY_REQUIRED");
        String fingerprint=digest(json(r));
        var replay=jdbc.queryForList("SELECT request_fingerprint,key_result_id,response FROM kr_create_command WHERE actor_id=? AND request_key=?",actor.id(),key);
        if(!replay.isEmpty()) {
            if(!fingerprint.equals(replay.get(0).get("request_fingerprint"))) throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED");
            tracking.scopedKr(((Number)replay.get(0).get("key_result_id")).longValue(),actor,false);
            return parseObject(replay.get(0).get("response"));
        }
        required(r.name(),"NAME_REQUIRED"); required(r.description(),"DESCRIPTION_REQUIRED");
        if(r.name().length()>255 || r.description().length()>255) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        String type=r.type()==null ? "COMMITTED" : r.type();
        if(!Set.of("COMMITTED","ASPIRATIONAL","LEARNING").contains(type)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_TYPE");
        var metric=metric(r.metricDefinitionId()); values(r.baseValue(),r.targetValue(),metric);
        if(r.strategicCycleId()==null || jdbc.queryForObject("SELECT COUNT(*) FROM strategic_cycle WHERE id=? AND status IN ('DRAFT','ACTIVE')",Long.class,r.strategicCycleId())!=1) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_CYCLE");
        tracking.lockOpenCycle(r.strategicCycleId());
        if(r.responsibleId()==null) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_RESPONSIBLE");
        var responsible=jdbc.queryForList("SELECT department_id FROM professor WHERE id=? AND active=TRUE",r.responsibleId());
        if(responsible.isEmpty()) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_RESPONSIBLE");
        if(!actor.admin() && ((Number)responsible.get(0).get("department_id")).longValue()!=actor.departmentId()) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        var goals=referenceIds(r.goalIds(),"goal"); var bets=referenceIds(r.betIds(),"strategic_bet");
        var values=new HashMap<String,Object>(); values.put("name",r.name().trim()); values.put("description",r.description().trim()); values.put("metric",metric.get("name"));
        values.put("metric_definition_id",r.metricDefinitionId()); values.put("base_value",r.baseValue()); values.put("target_value",r.targetValue()); values.put("unit_of_measure_id",metric.get("unit_of_measure_id"));
        values.put("strategic_cycle_id",r.strategicCycleId()); values.put("type",type); values.put("status","BORRADOR"); values.put("created_at",Timestamp.from(Instant.now()));
        long id=tracking.insert("key_result",values);
        tracking.insert("kr_assignment",Map.of("key_result_id",id,"professor_id",r.responsibleId(),"actor_id",actor.id()));
        for(Long goal:goals) tracking.insert("kr_goal_link",Map.of("key_result_id",id,"goal_id",goal,"actor_id",actor.id()));
        for(Long bet:bets) tracking.insert("kr_bet_link",Map.of("key_result_id",id,"strategic_bet_id",bet,"actor_id",actor.id()));
        var after=get(id); snapshot(id,0L,Map.of("after",after),"Creación de KR directo",actor.id());
        jdbc.update("INSERT INTO kr_create_command(actor_id,request_key,request_fingerprint,key_result_id,response) VALUES(?,?,?,?,CAST(? AS JSONB))",actor.id(),key,fingerprint,id,json(after));
        audit.record(AuditAction.CREATE,"KEY_RESULT",id,"KR directo: "+r.name().trim(),null,after);
        return after;
    }
    public Map<String,Object> revise(long id,Revision r,long version) {
        var actor=tracking.actor(); requireManager(actor); tracking.scopedKr(id,actor,true);
        jdbc.queryForList("SELECT id FROM key_result WHERE id=? FOR UPDATE",id);
        tracking.scopedKr(id,actor,true);
        var before=get(id); required(r.reason(),"REASON_REQUIRED");
        if(((Number)before.get("version")).longValue()!=version)throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM institutional_policy WHERE policy_key='KR_TARGET_APPROVAL' AND effective_at<=CURRENT_TIMESTAMP",Long.class);
        if(!"DRAFT".equals(before.get("status"))||count>0) {
            throw error(HttpStatus.CONFLICT,count==0?"POLICY_UNSET":"APPROVAL_REQUIRED");
        }
        return applyDefinition(id,r,version,actor,Map.of());
    }
    Revision validateRevision(long id,Revision r) {
        var before=definition(id);required(r.reason(),"REASON_REQUIRED");if(r.reason().length()>10000)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        Long metricId=r.metricDefinitionId()==null ? (before.get("metricDefinitionId")==null ? null : ((Number)before.get("metricDefinitionId")).longValue()) : r.metricDefinitionId();
        var metric=metric(metricId);
        BigDecimal base=r.baseValue()==null ? new BigDecimal(before.get("baseValue").toString()) : r.baseValue();
        BigDecimal target=r.targetValue()==null ? new BigDecimal(before.get("targetValue").toString()) : r.targetValue(); values(base,target,metric);
        return new Revision(metricId,base,target,r.reason().trim());
    }
    // Called only by the governed service after checking the captured policy and approver scope.
    Map<String,Object> applyDefinition(long id,Revision request,long version,TrackingV2Service.Actor actor,Map<String,Object> approval) {
        var before=definition(id);var r=validateRevision(id,request);var metric=metric(r.metricDefinitionId());
        if(!Boolean.TRUE.equals(before.get("active"))||"CLOSED".equals(before.get("status")))throw error(HttpStatus.CONFLICT,"INACTIVE_RESOURCE");
        Long metricId=r.metricDefinitionId();BigDecimal base=r.baseValue(),target=r.targetValue();
        if(jdbc.update("UPDATE key_result SET metric_definition_id=?,metric=?,unit_of_measure_id=?,base_value=?,target_value=?,version=version+1 WHERE id=? AND version=?",metricId,metric.get("name"),metric.get("unit_of_measure_id"),base,target,id,version)!=1) throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        var after=definition(id); snapshot(id,version+1,Map.of("before",before,"after",after,"approval",approval),r.reason().trim(),actor.id());
        audit.record(AuditAction.UPDATE,"KEY_RESULT",id,"Definición revisada: "+r.reason().trim(),before,Map.of("keyResult",after,"approval",approval)); return after;
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> versions(long id) {
        tracking.scopedKr(id,tracking.actor(),false);
        var rows=tracking.jsonRows("SELECT v.id,v.version,v.reason,v.occurred_at AS \"occurredAt\",p.name AS \"actorName\",CAST(v.snapshot AS VARCHAR) AS snapshot FROM kr_version v JOIN professor p ON p.id=v.actor_id WHERE v.key_result_id=? ORDER BY v.version DESC LIMIT 100",id);
        rows.forEach(r -> r.put("snapshot",parseObject(r.get("snapshot")))); return rows;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public TrackingV2Service.HistoryPage versionPage(long id,int page,int size) {
        if(page<0||page>1_000_000||size<1||size>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        tracking.scopedKr(id,tracking.actor(),false);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM kr_version WHERE key_result_id=?",Long.class,id);
        var rows=tracking.jsonRows("SELECT v.id,v.version,v.reason,v.occurred_at AS \"occurredAt\",p.name AS \"actorName\",CAST(v.snapshot AS VARCHAR) AS snapshot FROM kr_version v JOIN professor p ON p.id=v.actor_id WHERE v.key_result_id=? ORDER BY v.version DESC,v.id DESC LIMIT ? OFFSET ?",id,size,(long)page*size);
        rows.forEach(r->r.put("snapshot",parseObject(r.get("snapshot"))));
        return new TrackingV2Service.HistoryPage(rows,total,(total+size-1)/size,page,size);
    }
    private TrackingV2Service.Actor lockForChange(long id,long version,String reason) {
        var actor=tracking.actor(); requireManager(actor); tracking.scopedKr(id,actor,true);
        jdbc.queryForList("SELECT id FROM key_result WHERE id=? FOR UPDATE",id);
        var kr=tracking.scopedKr(id,actor,true); required(reason,"REASON_REQUIRED");
        if(((Number)kr.get("version")).longValue()!=version) throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        return actor;
    }
    private String linkTable(String kind) {
        return switch(kind) { case "goals" -> "kr_goal_link"; case "bets" -> "kr_bet_link"; default -> throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST"); };
    }
    private String targetColumn(String kind) { linkTable(kind); return kind.equals("goals") ? "goal_id" : "strategic_bet_id"; }
    @Transactional(readOnly=true)
    public Map<String,Object> relations(long id) {
        tracking.scopedKr(id,tracking.actor(),false);
        return Map.of("goals",tracking.jsonRows("SELECT l.id,l.goal_id AS \"targetId\",g.name,l.started_at AS \"startedAt\",l.ended_at AS \"endedAt\",p.name AS \"actorName\" FROM kr_goal_link l JOIN goal g ON g.id=l.goal_id JOIN professor p ON p.id=l.actor_id WHERE l.key_result_id=? ORDER BY l.started_at DESC,l.id DESC LIMIT 100",id),
            "bets",tracking.jsonRows("SELECT l.id,l.strategic_bet_id AS \"targetId\",b.name,l.started_at AS \"startedAt\",l.ended_at AS \"endedAt\",p.name AS \"actorName\" FROM kr_bet_link l JOIN strategic_bet b ON b.id=l.strategic_bet_id JOIN professor p ON p.id=l.actor_id WHERE l.key_result_id=? ORDER BY l.started_at DESC,l.id DESC LIMIT 100",id),
            "assignments",tracking.jsonRows("SELECT a.id,p.id AS \"professorId\",p.name,a.started_at AS \"startedAt\",a.ended_at AS \"endedAt\",actor.name AS \"actorName\" FROM kr_assignment a JOIN professor p ON p.id=a.professor_id JOIN professor actor ON actor.id=a.actor_id WHERE a.key_result_id=? ORDER BY a.started_at DESC,a.id DESC LIMIT 100",id));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public TrackingV2Service.HistoryPage relationPage(long id,String kind,int page,int size) {
        if(page<0||page>1_000_000||size<1||size>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        tracking.scopedKr(id,tracking.actor(),false);
        String table,query;
        switch(kind) {
            case "goals" -> { table="kr_goal_link";query="SELECT l.id,l.goal_id AS \"targetId\",g.name,l.started_at AS \"startedAt\",l.ended_at AS \"endedAt\",p.name AS \"actorName\" FROM kr_goal_link l JOIN goal g ON g.id=l.goal_id JOIN professor p ON p.id=l.actor_id WHERE l.key_result_id=? ORDER BY l.started_at DESC,l.id DESC"; }
            case "bets" -> { table="kr_bet_link";query="SELECT l.id,l.strategic_bet_id AS \"targetId\",b.name,l.started_at AS \"startedAt\",l.ended_at AS \"endedAt\",p.name AS \"actorName\" FROM kr_bet_link l JOIN strategic_bet b ON b.id=l.strategic_bet_id JOIN professor p ON p.id=l.actor_id WHERE l.key_result_id=? ORDER BY l.started_at DESC,l.id DESC"; }
            case "assignments" -> { table="kr_assignment";query="SELECT a.id,p.id AS \"professorId\",p.name,a.started_at AS \"startedAt\",a.ended_at AS \"endedAt\",actor.name AS \"actorName\" FROM kr_assignment a JOIN professor p ON p.id=a.professor_id JOIN professor actor ON actor.id=a.actor_id WHERE a.key_result_id=? ORDER BY a.started_at DESC,a.id DESC"; }
            default -> throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE key_result_id=?",Long.class,id);
        return new TrackingV2Service.HistoryPage(tracking.jsonRows(query+" LIMIT ? OFFSET ?",id,size,(long)page*size),total,(total+size-1)/size,page,size);
    }
    public Map<String,Object> addLink(long id,String kind,LinkWrite r,long version) {
        var actor=lockForChange(id,version,r.reason()); var before=get(id);
        String table=linkTable(kind),column=targetColumn(kind);
        referenceIds(Collections.singletonList(r.targetId()),kind.equals("goals") ? "goal" : "strategic_bet");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE key_result_id=? AND "+column+"=? AND ended_at IS NULL",Long.class,id,r.targetId())>0) throw error(HttpStatus.CONFLICT,"DUPLICATE_LINK");
        tracking.insert(table,Map.of("key_result_id",id,column,r.targetId(),"actor_id",actor.id()));
        return finishChange(id,version,before,r.reason(),actor,"Vínculo estratégico agregado");
    }
    public Map<String,Object> removeLink(long id,String kind,long linkId,String reason,long version) {
        var actor=lockForChange(id,version,reason); var before=get(id); String table=linkTable(kind);
        if("ACTIVE".equals(before.get("status"))) {
            var policy=policies.current(true); if(policy==null) throw error(HttpStatus.CONFLICT,"POLICY_UNSET");
            if(linkCount(id,"goals",kind.equals("goals") ? linkId : null)<policy.minGoals() || linkCount(id,"bets",kind.equals("bets") ? linkId : null)<policy.minBets()) throw error(HttpStatus.CONFLICT,"POLICY_MINIMUM_NOT_MET");
        }
        if(jdbc.update("UPDATE "+table+" SET ended_at=CURRENT_TIMESTAMP WHERE id=? AND key_result_id=? AND ended_at IS NULL",linkId,id)!=1) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        return finishChange(id,version,before,reason,actor,"Vínculo estratégico finalizado");
    }
    public Map<String,Object> reassign(long id,OwnerWrite r,long version) {
        var actor=lockForChange(id,version,r.reason()); var before=get(id);
        if(r.newProfessorId()==null) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_RESPONSIBLE");
        var candidates=jdbc.queryForList("SELECT p.department_id FROM professor p JOIN department d ON d.id=p.department_id WHERE p.id=? AND p.active=TRUE AND d.active=TRUE",r.newProfessorId());
        if(candidates.isEmpty()) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_RESPONSIBLE");
        var kr=tracking.scopedKr(id,actor,true);
        long department=((Number)candidates.get(0).get("department_id")).longValue();
        if((!actor.admin() && department!=actor.departmentId()) || (kr.get("department_id")!=null && department!=((Number)kr.get("department_id")).longValue())) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        var current=jdbc.query("SELECT professor_id,started_at FROM kr_assignment WHERE key_result_id=? AND ended_at IS NULL",(rs,row)->Map.of("id",rs.getLong(1),"start",rs.getTimestamp(2).toInstant()),id);
        if(!current.isEmpty() && ((Number)current.get(0).get("id")).longValue()==r.newProfessorId()) throw error(HttpStatus.CONFLICT,"SAME_RESPONSIBLE");
        // A strictly later instant also handles a second reassignment within one DB transaction.
        Instant end=Instant.now();
        for(var old:current) { Instant start=(Instant)old.get("start"); if(!end.isAfter(start)) end=start.plusMillis(1); }
        jdbc.update("UPDATE kr_assignment SET ended_at=? WHERE key_result_id=? AND ended_at IS NULL",Timestamp.from(end),id);
        tracking.insert("kr_assignment",Map.of("key_result_id",id,"professor_id",r.newProfessorId(),"actor_id",actor.id(),"started_at",Timestamp.from(end)));
        for(var old:current) tracking.insert("assignment_history",Map.of("entity_type","KEY_RESULT","entity_id",id,"professor_id",old.get("id"),"action","UNASSIGNED","actor_id",actor.id()));
        tracking.insert("assignment_history",Map.of("entity_type","KEY_RESULT","entity_id",id,"professor_id",r.newProfessorId(),"action","ASSIGNED","actor_id",actor.id()));
        return finishChange(id,version,before,r.reason(),actor,"Responsable reasignado");
    }
    private Map<String,Object> finishChange(long id,long version,Map<String,Object> before,String reason,TrackingV2Service.Actor actor,String description) {
        if(jdbc.update("UPDATE key_result SET version=version+1 WHERE id=? AND version=?",id,version)!=1) throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        var after=get(id); snapshot(id,version+1,Map.of("before",before,"after",after),reason.trim(),actor.id());
        audit.record(AuditAction.UPDATE,"KEY_RESULT",id,description+": "+reason.trim(),before,after); return after;
    }
    public Map<String,Object> classify(long id,TypeWrite r,long version) {
        var actor=lockForChange(id,version,r.reason()); var before=get(id);
        if(r.newType()==null || !Set.of("COMMITTED","ASPIRATIONAL","LEARNING").contains(r.newType())) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_TYPE");
        if(r.newType().equals(before.get("type"))) throw error(HttpStatus.CONFLICT,"SAME_TYPE");
        jdbc.update("UPDATE key_result SET type=? WHERE id=?",r.newType(),id);
        return finishChange(id,version,before,r.reason(),actor,"Tipo de KR revisado");
    }
    public Map<String,Object> deactivate(long id,DeactivateWrite r,long version) {
        var actor=lockForChange(id,version,r.reason()); var before=get(id);
        if(!Boolean.TRUE.equals(r.confirmed())) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"CONFIRMATION_REQUIRED");
        jdbc.update("UPDATE key_result SET active=FALSE WHERE id=?",id);
        return finishChange(id,version,before,r.reason(),actor,"KR desactivado");
    }
    private long linkCount(long id,String kind,Long excludedLink) {
        String table=linkTable(kind),target=kind.equals("goals") ? "goal" : "strategic_bet";
        String sql="SELECT COUNT(*) FROM "+table+" l JOIN "+target+" t ON t.id=l."+targetColumn(kind)+" WHERE l.key_result_id=? AND l.ended_at IS NULL AND t.active=TRUE";
        return excludedLink==null ? jdbc.queryForObject(sql,Long.class,id) : jdbc.queryForObject(sql+" AND l.id<>?",Long.class,id,excludedLink);
    }
    @Transactional(readOnly=true)
    public ActivationCheck activationCheck(long id) {
        tracking.scopedKr(id,tracking.actor(),false); return activationCheck(id,policies.current(false));
    }
    private ActivationCheck activationCheck(long id,PoliciesV2Service.Policy policy) {
        var kr=get(id); var checks=new ArrayList<Check>();
        checks.add(new Check("DRAFT",Boolean.TRUE.equals(kr.get("active")) && "DRAFT".equals(kr.get("status")),"DRAFT".equals(kr.get("status")) ? 1 : 0,1L));
        boolean validMetric=false,validValues=false;
        try { var metric=metric(kr.get("metricDefinitionId")==null ? null : ((Number)kr.get("metricDefinitionId")).longValue()); validMetric=true;
            values(new BigDecimal(kr.get("baseValue").toString()),new BigDecimal(kr.get("targetValue").toString()),metric); validValues=true;
        } catch(co.edu.icesi.pdg.mte.common.BusinessException | NullPointerException e) { /* Report incomplete definition without changing it. */ }
        checks.add(new Check("METRIC",validMetric,validMetric ? 1 : 0,1L)); checks.add(new Check("VALUES",validValues,validValues ? 1 : 0,1L));
        long responsible=jdbc.queryForObject("SELECT COUNT(*) FROM kr_assignment a JOIN professor p ON p.id=a.professor_id JOIN department d ON d.id=p.department_id WHERE a.key_result_id=? AND a.ended_at IS NULL AND p.active=TRUE AND d.active=TRUE",Long.class,id);
        checks.add(new Check("RESPONSIBLE",responsible==1,responsible,1L));
        long cycle=jdbc.queryForObject("SELECT COUNT(*) FROM key_result k JOIN strategic_cycle c ON c.id=k.strategic_cycle_id WHERE k.id=? AND c.status IN ('DRAFT','ACTIVE')",Long.class,id);
        checks.add(new Check("CYCLE",cycle==1,cycle,1L)); checks.add(new Check("POLICY",policy!=null,policy==null ? 0 : 1,1L));
        long goals=linkCount(id,"goals",null),bets=linkCount(id,"bets",null);
        checks.add(new Check("GOALS",policy!=null && goals>=policy.minGoals(),goals,policy==null ? null : (long)policy.minGoals()));
        checks.add(new Check("BETS",policy!=null && bets>=policy.minBets(),bets,policy==null ? null : (long)policy.minBets()));
        return new ActivationCheck(checks.stream().allMatch(Check::passed),policy==null ? null : policy.version(),checks);
    }
    public Map<String,Object> activate(long id,ActivationWrite r,long version) {
        var actor=lockForChange(id,version,r.reason()); var before=get(id);
        if(!"DRAFT".equals(before.get("status"))) throw error(HttpStatus.CONFLICT,"INVALID_TRANSITION");
        var policy=policies.current(true); if(policy==null) throw error(HttpStatus.CONFLICT,"POLICY_UNSET");
        var check=activationCheck(id,policy);
        if(check.checks().stream().anyMatch(c->Set.of("METRIC","VALUES","RESPONSIBLE","CYCLE").contains(c.code()) && !c.passed())) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"KR_DEFINITION_INCOMPLETE");
        if(!check.ready()) throw error(HttpStatus.CONFLICT,"KR_NOT_READY");
        if(jdbc.update("UPDATE key_result SET status='ACTIVO',version=version+1 WHERE id=? AND version=?",id,version)!=1) throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        var after=get(id); snapshot(id,version+1,Map.of("before",before,"after",after,"policy",policy),r.reason().trim(),actor.id());
        audit.record(AuditAction.STATUS_CHANGE,"KEY_RESULT",id,"KR activado con política v"+policy.version(),before,Map.of("keyResult",after,"policy",policy)); return after;
    }
    public Map<String,Object> transition(long id,StatusWrite r,long version) {
        if("ACTIVE".equals(r.newStatus())) return activate(id,new ActivationWrite(r.reason()),version);
        if("INACTIVE".equals(r.newStatus())) return deactivate(id,new DeactivateWrite(r.reason(),r.confirmed()),version);
        if("CLOSED".equals(r.newStatus())) { lockForChange(id,version,r.reason()); throw error(HttpStatus.CONFLICT,"FINAL_REVIEW_REQUIRED"); }
        var actor=tracking.actor(); requireManager(actor); tracking.scopedKr(id,actor,false);
        throw error(HttpStatus.CONFLICT,"INVALID_TRANSITION");
    }
    private void snapshot(long id,long version,Object value,String reason,long actorId) { jdbc.update("INSERT INTO kr_version(key_result_id,version,snapshot,reason,actor_id) VALUES(?,?,CAST(? AS JSONB),?,?)",id,version,json(value),reason,actorId); }
    private Map<String,Object> metric(Long id) {
        if(id==null) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_METRIC");
        return jdbc.queryForList("SELECT m.*,u.type AS unit_type,u.decimal_precision FROM metric_definition m JOIN unit_of_measure u ON u.id=m.unit_of_measure_id WHERE m.id=? AND m.active=TRUE AND u.active=TRUE",id).stream().findFirst().orElseThrow(()->error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_METRIC"));
    }
    private void values(BigDecimal base,BigDecimal target,Map<String,Object> metric) {
        int precision=Math.min(2,((Number)metric.get("decimal_precision")).intValue());
        for(BigDecimal v:new BigDecimal[]{base,target}) {
            if(v==null || v.stripTrailingZeros().scale()>precision || v.abs().compareTo(new BigDecimal("9999999999.99"))>0) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_METRIC_VALUE");
            if("PORCENTAJE".equals(metric.get("unit_type")) && (v.signum()<0 || v.compareTo(BigDecimal.valueOf(100))>0)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_METRIC_VALUE");
            if("BOOLEANA".equals(metric.get("unit_type")) && v.compareTo(BigDecimal.ZERO)!=0 && v.compareTo(BigDecimal.ONE)!=0) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_METRIC_VALUE");
        }
    }
    private List<Long> referenceIds(List<Long> ids,String table) {
        if(ids==null) return List.of();
        if(ids.size()>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        if(ids.stream().anyMatch(Objects::isNull)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        var unique=ids.stream().distinct().sorted().toList();
        for(Long id:unique) if(id==null || jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=? AND active=TRUE",Long.class,id)!=1) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        return unique;
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Map<String,Object> parseObject(Object value) {
        try {
            String serialized=value instanceof byte[] bytes ? new String(bytes,StandardCharsets.UTF_8) : String.valueOf(value);
            var node=mapper.readTree(serialized);
            if(node.isTextual()) node=mapper.readTree(node.asText());
            return mapper.convertValue(node,new TypeReference<Map<String,Object>>(){});
        } catch(Exception e) { throw new IllegalStateException("Invalid stored KR snapshot",e); }
    }
    private String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException(e); } }
    private String storageStatus(String status) { return switch(status) { case "DRAFT"->"BORRADOR"; case "ACTIVE"->"ACTIVO"; case "CLOSED"->"CERRADO"; default->status; }; }
    private String publicStatus(String status) { return switch(status) { case "BORRADOR"->"DRAFT"; case "ACTIVO"->"ACTIVE"; case "CERRADO"->"CLOSED"; default->status; }; }
}
