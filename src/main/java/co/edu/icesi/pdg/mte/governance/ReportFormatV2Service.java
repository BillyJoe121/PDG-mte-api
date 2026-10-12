package co.edu.icesi.pdg.mte.governance;

import co.edu.icesi.pdg.mte.audit.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Service.*;

@Service
@Transactional
public class ReportFormatV2Service {
    public static final String KEY="SCHOOL_REPORT_FORMAT";
    // These identify implemented report sections, not an institutionally approved template.
    public static final Set<String> SECTIONS=Set.of("TITLE","ACHIEVEMENTS","DIFFICULTIES","CONCLUSIONS","CUT","BRANCH_RESULTS","STRATEGIC_RESULTS","SITUATIONS","CORRECTIVES");
    public record Write(String templateName,List<String> requiredSections,List<String> recipients,String decisionRef,String reason,Boolean confirmed) {}
    public record Format(long id,int version,String templateName,List<String> requiredSections,List<String> recipients,String decisionRef,String reason,String recordedBy,String recordedAt,String effectiveAt) {}
    public record FormatPage(List<Format> items,long totalElements,long totalPages,int currentPage,int pageSize,long latestVersion) {}
    private final JdbcTemplate jdbc;private final TrackingV2Service tracking;private final ObjectMapper mapper;private final AuditService audit;
    public ReportFormatV2Service(JdbcTemplate jdbc,TrackingV2Service tracking,ObjectMapper mapper,AuditService audit){this.jdbc=jdbc;this.tracking=tracking;this.mapper=mapper;this.audit=audit;}
    void lock(){if(jdbc.queryForList("SELECT policy_key FROM policy_registry_lock WHERE policy_key=? FOR UPDATE",KEY).isEmpty())throw error(HttpStatus.CONFLICT,"POLICY_UNSET");}
    @Transactional(readOnly=true)
    public List<Format> history(){tracking.actor();return rows(" ORDER BY p.version DESC LIMIT 100");}
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public FormatPage page(int page,int size){
        tracking.actor();if(page<0||size<1||size>100)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM institutional_policy WHERE policy_key=?",Long.class,KEY);long latest=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM institutional_policy WHERE policy_key=?",Long.class,KEY);
        return new FormatPage(rows(" ORDER BY p.version DESC,p.id DESC LIMIT ? OFFSET ?",size,(long)page*size),total,(total+size-1)/size,page,size,latest);
    }
    Format current(){return rows(" AND p.effective_at<=CURRENT_TIMESTAMP ORDER BY p.version DESC LIMIT 1").stream().findFirst().orElse(null);}
    private List<Format> rows(String suffix,Object...parameters){
        var args=new ArrayList<Object>();args.add(KEY);args.addAll(Arrays.asList(parameters));
        return jdbc.query("SELECT p.id,p.version,CAST(p.value AS VARCHAR) AS config,prof.name AS actor,p.approved_at,p.effective_at FROM institutional_policy p JOIN professor prof ON prof.id=p.approved_by WHERE p.policy_key=?"+suffix,(rs,index)->{
            try{var node=mapper.readTree(rs.getString("config"));if(node.isTextual())node=mapper.readTree(node.asText());
                if(!node.path("templateName").isTextual()||!node.path("requiredSections").isArray()||!node.path("recipients").isArray()||!node.path("decisionRef").isTextual()||!node.path("reason").isTextual())throw error(HttpStatus.CONFLICT,"INVALID_POLICY");
                var sections=new ArrayList<String>();for(var field:node.path("requiredSections")){if(!field.isTextual())throw error(HttpStatus.CONFLICT,"INVALID_POLICY");sections.add(field.asText());}
                var recipients=new ArrayList<String>();for(var field:node.path("recipients")){if(!field.isTextual())throw error(HttpStatus.CONFLICT,"INVALID_POLICY");recipients.add(field.asText());}
                var write=new Write(node.path("templateName").asText(),sections,recipients,node.path("decisionRef").asText(),node.path("reason").asText(),true);
                try{validate(write);}catch(co.edu.icesi.pdg.mte.common.BusinessException invalid){throw error(HttpStatus.CONFLICT,"INVALID_POLICY");}
                return new Format(rs.getLong("id"),rs.getInt("version"),write.templateName(),List.copyOf(sections),List.copyOf(recipients),write.decisionRef(),write.reason(),rs.getString("actor"),rs.getTimestamp("approved_at").toInstant().toString(),rs.getTimestamp("effective_at").toInstant().toString());
            }catch(co.edu.icesi.pdg.mte.common.BusinessException e){throw e;}catch(Exception e){throw error(HttpStatus.CONFLICT,"INVALID_POLICY");}
        },args.toArray());
    }
    private void validate(Write r){
        required(r.templateName(),"NAME_REQUIRED");required(r.reason(),"REASON_REQUIRED");
        if(r.templateName().length()>255||r.reason().length()>10000||r.requiredSections()==null||r.requiredSections().isEmpty()||r.requiredSections().size()>SECTIONS.size()||r.requiredSections().stream().anyMatch(s->s==null||!SECTIONS.contains(s))||new HashSet<>(r.requiredSections()).size()!=r.requiredSections().size())throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REPORT_FORMAT");
        if(r.recipients()==null||r.recipients().isEmpty()||r.recipients().size()>100||r.recipients().stream().anyMatch(s->s==null||s.isBlank()||s.length()>255||s.indexOf('\n')>=0||s.indexOf('\r')>=0)||new HashSet<>(r.recipients().stream().map(String::trim).toList()).size()!=r.recipients().size())throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REPORT_RECIPIENTS");
        if(!Boolean.TRUE.equals(r.confirmed()))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_CONFIRMATION_REQUIRED");
        String ref=r.decisionRef()==null?"":r.decisionRef().trim();
        try{var uri=URI.create(ref);if(ref.length()>1000||!Set.of("http","https").contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT))||uri.getHost()==null||uri.getRawUserInfo()!=null)throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EVIDENCE_URL");}
    }
    public Format record(Write r,long expected){
        var actor=tracking.actor();if(!actor.admin())throw error(HttpStatus.FORBIDDEN,"FORBIDDEN");validate(r);lock();
        int latest=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM institutional_policy WHERE policy_key=?",Integer.class,KEY);if(latest!=expected)throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        var before=current();String json;try{json=mapper.writeValueAsString(Map.of("templateName",r.templateName().trim(),"requiredSections",r.requiredSections(),"recipients",r.recipients().stream().map(String::trim).toList(),"decisionRef",r.decisionRef().trim(),"reason",r.reason().trim()));}catch(Exception e){throw new IllegalStateException(e);}
        jdbc.update("INSERT INTO institutional_policy(policy_key,policy_code,version,\"value\",approved_by) VALUES(?,?,?,CAST(? AS JSONB),?)",KEY,KEY,latest+1,json,actor.id());
        var after=current();audit.record(AuditAction.CREATE,"INSTITUTIONAL_POLICY",after.id(),"Formato de informe aprobado externamente registrado",before,after);return after;
    }
}
