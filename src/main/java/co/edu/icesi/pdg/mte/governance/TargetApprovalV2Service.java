package co.edu.icesi.pdg.mte.governance;

import co.edu.icesi.pdg.mte.audit.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Service.*;

@Service @Transactional
public class TargetApprovalV2Service {
    public record Write(Long metricDefinitionId,BigDecimal baseValue,BigDecimal targetValue,String reason,Long effectiveFromCheckIn) {}
    public record Decision(String status,String reason) {}
    public record Page(List<Map<String,Object>> items,long totalElements,long totalPages,int currentPage,int pageSize) {}
    private final JdbcTemplate jdbc;private final TrackingV2Service tracking;private final StrategyV2Service strategy;private final ApprovalPolicyV2Service policies;private final AuditService audit;private final ObjectMapper mapper;
    public TargetApprovalV2Service(JdbcTemplate jdbc,TrackingV2Service tracking,StrategyV2Service strategy,ApprovalPolicyV2Service policies,AuditService audit,ObjectMapper mapper){this.jdbc=jdbc;this.tracking=tracking;this.strategy=strategy;this.policies=policies;this.audit=audit;this.mapper=mapper;}
    private Map<String,Object> kr(long id,boolean lock){
        if(lock){var cycles=jdbc.queryForList("SELECT strategic_cycle_id FROM key_result WHERE id=?",id);if(!cycles.isEmpty()&&cycles.get(0).get("strategic_cycle_id")!=null)tracking.lockOpenCycle(((Number)cycles.get(0).get("strategic_cycle_id")).longValue());}
        if(lock)jdbc.queryForList("SELECT id FROM key_result WHERE id=? FOR UPDATE",id);
        var rows=jdbc.queryForList("SELECT k.id,k.version,k.active,k.status,k.strategic_cycle_id,c.status AS cycle_status,COALESCE(o.department_id,p.department_id) AS department_id,d.school_id,s.faculty_id FROM key_result k LEFT JOIN objective o ON o.id=k.objective_id LEFT JOIN kr_assignment a ON a.key_result_id=k.id AND a.ended_at IS NULL LEFT JOIN professor p ON p.id=a.professor_id LEFT JOIN department d ON d.id=COALESCE(o.department_id,p.department_id) LEFT JOIN school s ON s.id=d.school_id LEFT JOIN strategic_cycle c ON c.id=k.strategic_cycle_id WHERE k.id=?",id);
        return rows.stream().findFirst().orElseThrow(()->error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE"));
    }
    private String status(Map<String,Object> kr){return switch(String.valueOf(kr.get("status"))){case "BORRADOR"->"DRAFT";case "ACTIVO"->"ACTIVE";default->String.valueOf(kr.get("status"));};}
    private void writable(Map<String,Object> kr){if(kr.get("strategic_cycle_id")==null)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_CYCLE");if(!Boolean.TRUE.equals(kr.get("active"))||!Set.of("DRAFT","ACTIVE").contains(status(kr))||!Set.of("DRAFT","ACTIVE").contains(String.valueOf(kr.get("cycle_status"))))throw error(HttpStatus.CONFLICT,"INACTIVE_RESOURCE");}
    private boolean approver(TrackingV2Service.Actor actor,Map<String,Object> kr,ApprovalPolicyV2Service.Rule rule){
        for(String role:rule.approverRoles()){
            Object unit=switch(role){case "DECANO"->kr.get("faculty_id");case "DIRECTOR"->kr.get("school_id");case "JEFE"->kr.get("department_id");default->null;};
            String scope=switch(role){case "DECANO"->" AND faculty_id=?";case "DIRECTOR"->" AND school_id=?";case "JEFE"->" AND department_id=?";default->"";};
            if(!scope.isEmpty()&&unit==null)continue;
            var args=new ArrayList<Object>(List.of(actor.id(),role));if(!scope.isEmpty())args.add(unit);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE professor_id=? AND role_name=? AND started_at<=CURRENT_TIMESTAMP AND ended_at IS NULL"+scope,Long.class,args.toArray())>0)return true;
        }
        return false;
    }
    private boolean reader(long id,TrackingV2Service.Actor actor,Map<String,Object> kr){
        if(actor.admin())return true;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM key_result k LEFT JOIN objective o ON o.id=k.objective_id WHERE k.id=? AND "+tracking.scope(actor),Long.class,id)>0)return true;
        return approver(actor,kr,new ApprovalPolicyV2Service.Rule(status(kr),null,true,List.of("RECTOR","DECANO","DIRECTOR","JEFE"),false));
    }
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String fingerprint(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private com.fasterxml.jackson.databind.JsonNode snapshot(Object value){try{var node=mapper.readTree(value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):String.valueOf(value));return node.isTextual()?mapper.readTree(node.asText()):node;}catch(Exception e){throw new IllegalStateException("Invalid target request snapshot",e);}}
    private Map<String,Object> row(long id){return tracking.jsonRows("SELECT x.id,x.key_result_id AS \"keyResultId\",x.approval_status AS status,x.policy_id AS \"policyId\",x.kr_version AS \"krVersion\",x.actor_id AS \"actorId\",x.reason,x.effective_from_check_in AS \"effectiveFromCheckIn\",x.requested_at AS \"requestedAt\",x.approved_at AS \"decidedAt\",x.decision_reason AS \"decisionReason\",x.decision_actor_id AS \"decisionActorId\",x.version,CAST(x.request_snapshot AS VARCHAR) AS snapshot,p.name AS \"requestedBy\" FROM kr_target_revision x JOIN professor p ON p.id=x.actor_id WHERE x.id=? AND x.policy_id IS NOT NULL",id).stream().findFirst().orElseThrow(()->error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE"));}
    private Map<String,Object> visible(long id,TrackingV2Service.Actor actor,Map<String,Object> kr){
        var result=row(id);var captured=snapshot(result.get("snapshot"));result.put("snapshot",captured);var rule=mapper.convertValue(captured.path("rule"),ApprovalPolicyV2Service.Rule.class);
        var current=policies.current();result.put("canDecide","PENDING".equals(result.get("status"))&&current!=null&&current.id()==((Number)result.get("policyId")).longValue()&&approver(actor,kr,rule)&&(rule.allowSelfApproval()||actor.id()!=((Number)result.get("actorId")).longValue()));
        result.put("canCancel","PENDING".equals(result.get("status"))&&actor.id()==((Number)result.get("actorId")).longValue());return result;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page page(long id,int page,int size){
        var actor=tracking.actor();var kr=kr(id,false);if(!reader(id,actor,kr))throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        if(page<0||page>1000000||size<1||size>100)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM kr_target_revision WHERE key_result_id=? AND policy_id IS NOT NULL",Long.class,id);var ids=jdbc.queryForList("SELECT id FROM kr_target_revision WHERE key_result_id=? AND policy_id IS NOT NULL ORDER BY id DESC LIMIT ? OFFSET ?",Long.class,id,size,(long)page*size);
        return new Page(ids.stream().map(request->visible(request,actor,kr)).toList(),total,(total+size-1)/size,page,size);
    }
    public Map<String,Object> request(long id,Write r,long expected,String key){
        return write(id,r,expected,key,true);
    }
    public Map<String,Object> change(long id,Write r,long expected,String key){
        return write(id,r,expected,key,false);
    }
    private Map<String,Object> write(long id,Write r,long expected,String key,boolean allowPending){
        var actor=tracking.actor();if(!actor.admin()&&!actor.manager())throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");tracking.scopedKr(id,actor,false);
        if(key==null||key.isBlank()||key.length()>128)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"IDEMPOTENCY_KEY_REQUIRED");
        var kr=kr(id,true);String hash=fingerprint(Map.of("id",id,"version",expected,"request",r,"allowPending",allowPending));
        var replay=jdbc.queryForList("SELECT id,request_fingerprint FROM kr_target_revision WHERE actor_id=? AND request_key=?",actor.id(),key);
        if(!replay.isEmpty()){if(!hash.equals(replay.get(0).get("request_fingerprint")))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED");return visible(((Number)replay.get(0).get("id")).longValue(),actor,kr);}
        tracking.scopedKr(id,actor,true);writable(kr);if(((Number)kr.get("version")).longValue()!=expected)throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        var revision=strategy.validateRevision(id,new StrategyV2Service.Revision(r.metricDefinitionId(),r.baseValue(),r.targetValue(),r.reason()));
        if(r.effectiveFromCheckIn()!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM kr_measurement WHERE id=? AND key_result_id=? AND status='ACTIVE'",Long.class,r.effectiveFromCheckIn(),id)!=1)throw error(HttpStatus.NOT_FOUND,"CHECK_IN_NOT_FOUND");
        policies.lock();var policy=policies.current();var rule=policies.rule(policy,status(kr),((Number)kr.get("strategic_cycle_id")).longValue());var before=strategy.definition(id);
        if(rule.requiresApproval()&&!allowPending)throw error(HttpStatus.CONFLICT,"APPROVAL_REQUIRED");
        if(Objects.equals(before.get("metricDefinitionId"),revision.metricDefinitionId())&&new BigDecimal(before.get("baseValue").toString()).compareTo(revision.baseValue())==0&&new BigDecimal(before.get("targetValue").toString()).compareTo(revision.targetValue())==0)throw error(HttpStatus.CONFLICT,"SAME_DEFINITION");
        var values=new HashMap<String,Object>();values.put("key_result_id",id);values.put("previous_baseline",before.get("baseValue"));values.put("new_baseline",revision.baseValue());values.put("previous_target",before.get("targetValue"));values.put("new_target",revision.targetValue());values.put("reason",revision.reason());values.put("requested_by",actor.name());values.put("actor_id",actor.id());values.put("kr_version",expected);values.put("request_key",key);values.put("request_fingerprint",hash);values.put("approval_status","PENDING");if(r.effectiveFromCheckIn()!=null)values.put("effective_from_check_in",String.valueOf(r.effectiveFromCheckIn()));
        long request=tracking.insert("kr_target_revision",values);
        jdbc.update("UPDATE kr_target_revision SET policy_id=?,request_snapshot=CAST(? AS JSONB) WHERE id=?",policy.id(),json(Map.of("before",before,"proposed",revision,"policy",policy,"rule",rule)),request);
        // PENDING is persisted without changing KR definition, versions, measurements or manual progress.
        if(!rule.requiresApproval())apply(request,id,revision,expected,actor,policy.id(),"Aplicación según matriz sin aprobación requerida");
        var after=visible(request,actor,kr);audit.record(AuditAction.CREATE,"KR_TARGET_REVISION",request,"Solicitud de cambio de definición KR #"+id,null,after);return after;
    }
    private void apply(long request,long id,StrategyV2Service.Revision revision,long version,TrackingV2Service.Actor actor,long policy,String reason){
        strategy.applyDefinition(id,revision,version,actor,Map.of("requestId",request,"policyId",policy));
        jdbc.update("UPDATE kr_target_revision SET approval_status='APPROVED',decision_actor_id=?,approved_by=?,approved_at=CURRENT_TIMESTAMP,decision_reason=?,version=version+1 WHERE id=?",actor.id(),actor.name(),reason,request);
    }
    public Map<String,Object> decide(long id,long request,Decision r,long expected){
        var actor=tracking.actor();var kr=kr(id,true);if(!reader(id,actor,kr))throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        jdbc.queryForList("SELECT id FROM kr_target_revision WHERE id=? AND key_result_id=? FOR UPDATE",request,id);var before=row(request);if(((Number)before.get("keyResultId")).longValue()!=id)throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        required(r.reason(),"REASON_REQUIRED");if(r.reason().length()>10000||!Set.of("APPROVED","REJECTED","CANCELLED").contains(String.valueOf(r.status())))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        if(!"PENDING".equals(before.get("status"))){if(r.status().equals(before.get("status"))&&Objects.equals(before.get("decisionActorId"),actor.id())&&r.reason().trim().equals(before.get("decisionReason")))return visible(request,actor,kr);throw error(HttpStatus.CONFLICT,"REQUEST_ALREADY_DECIDED");}
        if(((Number)before.get("version")).longValue()!=expected)throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        var captured=snapshot(before.get("snapshot"));var rule=mapper.convertValue(captured.path("rule"),ApprovalPolicyV2Service.Rule.class);
        if("CANCELLED".equals(r.status())){if(actor.id()!=((Number)before.get("actorId")).longValue())throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");}
        else{
            if(!approver(actor,kr,rule))throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");if(!rule.allowSelfApproval()&&actor.id()==((Number)before.get("actorId")).longValue())throw error(HttpStatus.CONFLICT,"SELF_APPROVAL_FORBIDDEN");
            policies.lock();var current=policies.current();if(current==null||current.id()!=((Number)before.get("policyId")).longValue())throw error(HttpStatus.CONFLICT,"POLICY_CHANGED");
        }
        if("APPROVED".equals(r.status())){writable(kr);if(((Number)kr.get("version")).longValue()!=((Number)before.get("krVersion")).longValue())throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");apply(request,id,mapper.convertValue(captured.path("proposed"),StrategyV2Service.Revision.class),((Number)before.get("krVersion")).longValue(),actor,((Number)before.get("policyId")).longValue(),r.reason().trim());}
        else jdbc.update("UPDATE kr_target_revision SET approval_status=?,decision_actor_id=?,approved_at=CURRENT_TIMESTAMP,decision_reason=?,version=version+1 WHERE id=?",r.status(),actor.id(),r.reason().trim(),request);
        var after=visible(request,actor,kr);audit.record(AuditAction.STATUS_CHANGE,"KR_TARGET_REVISION",request,"Decisión de cambio KR #"+id,before,after);return after;
    }
}
