package co.edu.icesi.pdg.mte.governance;

import co.edu.icesi.pdg.mte.audit.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.net.URI;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Service.*;

@Service @Transactional
public class ApprovalPolicyV2Service {
    public static final String KEY="KR_TARGET_APPROVAL";
    public static final Set<String> ROLES=Set.of("RECTOR","DECANO","DIRECTOR","JEFE");
    public record Rule(String status,Long cycleId,Boolean requiresApproval,List<String> approverRoles,Boolean allowSelfApproval) {}
    public record Write(List<Rule> rules,String decisionRef,String reason,Boolean confirmed) {}
    public record Policy(long id,int version,List<Rule> rules,String decisionRef,String reason,String recordedBy,String recordedAt) {}
    public record Page(List<Policy> items,long totalElements,long totalPages,int currentPage,int pageSize,long latestVersion) {}
    private final JdbcTemplate jdbc;private final TrackingV2Service tracking;private final AuditService audit;private final ObjectMapper mapper;
    public ApprovalPolicyV2Service(JdbcTemplate jdbc,TrackingV2Service tracking,AuditService audit,ObjectMapper mapper){this.jdbc=jdbc;this.tracking=tracking;this.audit=audit;this.mapper=mapper;}
    void lock(){if(jdbc.queryForList("SELECT policy_key FROM policy_registry_lock WHERE policy_key=? FOR UPDATE",KEY).isEmpty())throw error(HttpStatus.CONFLICT,"POLICY_UNSET");}
    Policy current(){return rows(" AND p.effective_at<=CURRENT_TIMESTAMP ORDER BY p.version DESC LIMIT 1").stream().findFirst().orElse(null);}
    Rule rule(Policy policy,String status,long cycle){
        if(policy==null)throw error(HttpStatus.CONFLICT,"POLICY_UNSET");
        return policy.rules().stream().filter(r->r.status().equals(status)&&Objects.equals(r.cycleId(),cycle)).findFirst().orElseGet(()->policy.rules().stream().filter(r->r.status().equals(status)&&r.cycleId()==null).findFirst().orElseThrow(()->error(HttpStatus.CONFLICT,"POLICY_UNSET")));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page page(int page,int size){
        tracking.actor();if(page<0||page>1000000||size<1||size>100)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM institutional_policy WHERE policy_key=?",Long.class,KEY),latest=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM institutional_policy WHERE policy_key=?",Long.class,KEY);
        return new Page(rows(" ORDER BY p.version DESC,p.id DESC LIMIT ? OFFSET ?",size,(long)page*size),total,(total+size-1)/size,page,size,latest);
    }
    private List<Policy> rows(String suffix,Object...parameters){
        var args=new ArrayList<Object>();args.add(KEY);args.addAll(Arrays.asList(parameters));
        return jdbc.query("SELECT p.id,p.version,CAST(p.value AS VARCHAR) AS config,prof.name,p.approved_at FROM institutional_policy p JOIN professor prof ON prof.id=p.approved_by WHERE p.policy_key=?"+suffix,(rs,row)->{
            try{var node=mapper.readTree(rs.getString("config"));if(node.isTextual())node=mapper.readTree(node.asText());var write=mapper.treeToValue(node,Write.class);validate(write);
                return new Policy(rs.getLong("id"),rs.getInt("version"),List.copyOf(write.rules()),write.decisionRef(),write.reason(),rs.getString("name"),rs.getTimestamp("approved_at").toInstant().toString());
            }catch(Exception e){throw error(HttpStatus.CONFLICT,"INVALID_POLICY");}
        },args.toArray());
    }
    private void validate(Write r){
        if(r==null||r.rules()==null||r.rules().isEmpty()||r.rules().size()>100)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_APPROVAL_MATRIX");
        var seen=new HashSet<String>();for(var rule:r.rules()){
            if(rule==null||!Set.of("DRAFT","ACTIVE").contains(String.valueOf(rule.status()))||rule.requiresApproval()==null||rule.allowSelfApproval()==null||rule.approverRoles()==null||rule.approverRoles().stream().anyMatch(role->role==null||!ROLES.contains(role))||new HashSet<>(rule.approverRoles()).size()!=rule.approverRoles().size()||rule.cycleId()!=null&&rule.cycleId()<=0||!seen.add(rule.status()+":"+rule.cycleId()))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_APPROVAL_MATRIX");
            if(rule.requiresApproval()&&rule.approverRoles().isEmpty()||!rule.requiresApproval()&&(!rule.approverRoles().isEmpty()||rule.allowSelfApproval()))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_APPROVAL_MATRIX");
        }
        required(r.reason(),"REASON_REQUIRED");if(r.reason().length()>10000)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        if(!Boolean.TRUE.equals(r.confirmed()))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_CONFIRMATION_REQUIRED");
        try{var uri=URI.create(r.decisionRef()==null?"":r.decisionRef().trim());if(r.decisionRef().length()>1000||!Set.of("http","https").contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT))||uri.getHost()==null||uri.getRawUserInfo()!=null)throw new IllegalArgumentException();}catch(Exception e){throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EVIDENCE_URL");}
    }
    public Policy record(Write r,long expected){
        var actor=tracking.actor();if(!actor.admin())throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");validate(r);
        for(var rule:r.rules())if(rule.cycleId()!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM strategic_cycle WHERE id=?",Long.class,rule.cycleId())==0)throw error(HttpStatus.NOT_FOUND,"CYCLE_NOT_FOUND");
        lock();long latest=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM institutional_policy WHERE policy_key=?",Long.class,KEY);if(latest!=expected)throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        var before=current();String json;try{json=mapper.writeValueAsString(new Write(r.rules(),r.decisionRef().trim(),r.reason().trim(),true));}catch(Exception e){throw new IllegalStateException(e);}
        jdbc.update("INSERT INTO institutional_policy(policy_key,policy_code,version,\"value\",approved_by) VALUES(?,?,?,CAST(? AS JSONB),?)",KEY,KEY,latest+1,json,actor.id());
        var after=current();audit.record(AuditAction.CREATE,"INSTITUTIONAL_POLICY",after.id(),"Matriz de aprobación de cambios de KR registrada",before,after);return after;
    }
}
