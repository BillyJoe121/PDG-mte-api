package co.edu.icesi.pdg.mte.governance;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Service.error;

@Service
@Transactional
public class PoliciesV2Service {
    public static final String KEY="KR_LINK_MINIMUMS";
    public record Write(Integer minGoals,Integer minBets,String decisionRef,String reason,Boolean confirmed) {}
    public record Policy(long id,int version,int minGoals,int minBets,String decisionRef,String reason,String recordedBy,String approvedAt,String effectiveAt) {}
    private final JdbcTemplate jdbc; private final TrackingV2Service tracking; private final AuditService audit; private final ObjectMapper mapper;
    public PoliciesV2Service(JdbcTemplate jdbc,TrackingV2Service tracking,AuditService audit,ObjectMapper mapper) { this.jdbc=jdbc;this.tracking=tracking;this.audit=audit;this.mapper=mapper; }
    void lock() {
        if(jdbc.queryForList("SELECT policy_key FROM policy_registry_lock WHERE policy_key=? FOR UPDATE",KEY).isEmpty()) throw error(HttpStatus.CONFLICT,"POLICY_UNSET");
    }
    @Transactional(readOnly=true)
    public List<Policy> history() { tracking.actor(); return rows(" ORDER BY p.version DESC LIMIT 100"); }
    Policy current(boolean lock) {
        if(lock) lock();
        return rows(" AND p.effective_at<=CURRENT_TIMESTAMP ORDER BY p.version DESC LIMIT 1").stream().findFirst().orElse(null);
    }
    private List<Policy> rows(String suffix) {
        return jdbc.query("SELECT p.id,p.version,CAST(p.value AS VARCHAR) AS config,prof.name AS actor_name,p.approved_at,p.effective_at FROM institutional_policy p JOIN professor prof ON prof.id=p.approved_by WHERE p.policy_key=?"+suffix,(rs,row)->{
            try {
                var value=mapper.readTree(rs.getString("config")); if(value.isTextual()) value=mapper.readTree(value.asText());
                if(!value.path("minGoals").isInt() || !value.path("minBets").isInt() || value.path("minGoals").intValue()<0 || value.path("minBets").intValue()<0 || value.path("minGoals").intValue()>100 || value.path("minBets").intValue()>100 || !value.path("decisionRef").isTextual() || value.path("decisionRef").asText().isBlank() || !value.path("reason").isTextual() || value.path("reason").asText().isBlank())
                    throw error(HttpStatus.CONFLICT,"INVALID_POLICY");
                return new Policy(rs.getLong("id"),rs.getInt("version"),value.path("minGoals").intValue(),value.path("minBets").intValue(),value.path("decisionRef").asText(),value.path("reason").asText(),rs.getString("actor_name"),rs.getTimestamp("approved_at").toInstant().toString(),rs.getTimestamp("effective_at").toInstant().toString());
            } catch(co.edu.icesi.pdg.mte.common.BusinessException e) { throw e; }
            catch(Exception e) { throw error(HttpStatus.CONFLICT,"INVALID_POLICY"); }
        },KEY);
    }
    public Policy record(Write r,long expectedVersion) {
        var actor=tracking.actor(); if(!actor.admin()) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");
        if(r.minGoals()==null || r.minBets()==null || r.minGoals()<0 || r.minBets()<0 || r.minGoals()>100 || r.minBets()>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_POLICY_MINIMUMS");
        GovernanceV2Service.required(r.reason(),"REASON_REQUIRED");
        if(!Boolean.TRUE.equals(r.confirmed())) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_CONFIRMATION_REQUIRED");
        String ref=r.decisionRef()==null ? "" : r.decisionRef().trim();
        try { URI uri=URI.create(ref); if(ref.length()>1000 || !Set.of("http","https").contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT)) || uri.getHost()==null || uri.getRawUserInfo()!=null) throw new IllegalArgumentException(); }
        catch(IllegalArgumentException e) { throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EVIDENCE_URL"); }
        lock(); var before=current(false);
        int latest=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM institutional_policy WHERE policy_key=?",Integer.class,KEY);
        if(latest!=expectedVersion) throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        String json;
        try { json=mapper.writeValueAsString(Map.of("minGoals",r.minGoals(),"minBets",r.minBets(),"decisionRef",ref,"reason",r.reason().trim())); }
        catch(Exception e) { throw new IllegalStateException(e); }
        jdbc.update("INSERT INTO institutional_policy(policy_key,policy_code,version,\"value\",approved_by) VALUES(?,?,?,CAST(? AS JSONB),?)",KEY,KEY,latest+1,json,actor.id());
        var after=current(false); audit.record(AuditAction.CREATE,"INSTITUTIONAL_POLICY",after.id(),"Decisión institucional registrada",before,after); return after;
    }
}
