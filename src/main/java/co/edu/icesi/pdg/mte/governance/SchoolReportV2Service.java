package co.edu.icesi.pdg.mte.governance;
import co.edu.icesi.pdg.mte.audit.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Service.*;
@Service
@Transactional
public class SchoolReportV2Service {
    public record Draft(long cutId,String title,String achievementsSummary,String difficultiesSummary,String conclusions,String idempotencyKey) {}
    public record Publish(long formatVersion,String idempotencyKey,boolean confirmed) {}
    public record Review(long reportVersion,String decision,String comment,List<String> evidenceUrls,String idempotencyKey) {}
    public record Finish(long reportVersion,long reviewId,String idempotencyKey,boolean confirmed) {}
    public record ReviewReceipt(Map<String,Object> review,Map<String,Object> report) {}
    public record ReviewPage(List<Map<String,Object>> items,long totalElements,long totalPages,int currentPage,int pageSize) {}
    public record ReportPage(List<Map<String,Object>> items,long totalElements,long totalPages,int currentPage,int pageSize,long latestVersion) {}
    private final JdbcTemplate jdbc;private final TrackingV2Service tracking;private final MonitoringV2Service monitoring;private final ObjectMapper mapper;private final AuditService audit;private final ReportFormatV2Service formats;
    public SchoolReportV2Service(JdbcTemplate jdbc,TrackingV2Service tracking,MonitoringV2Service monitoring,ObjectMapper mapper,AuditService audit,ReportFormatV2Service formats){this.jdbc=jdbc;this.tracking=tracking;this.monitoring=monitoring;this.mapper=mapper;this.audit=audit;this.formats=formats;}
    private String projection(){return "SELECT r.id,r.cut_id AS \"cutId\",r.cycle_id AS \"cycleId\",r.title,r.report_version AS version,r.status,r.achievements_summary AS \"achievementsSummary\",r.challenges_summary AS \"difficultiesSummary\",r.conclusions,r.author_id AS \"generatedBy\",p.name AS \"authorName\",r.created_at AS \"generatedAt\" FROM school_final_report r LEFT JOIN professor p ON CAST(p.id AS VARCHAR)=r.author_id";}
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(long cut){monitoring.finalReportCut(cut,false);var rows=tracking.jsonRows(projection()+" WHERE r.cut_id=? ORDER BY r.report_version DESC LIMIT 100",cut);rows.forEach(r->r.put("formatPolicyApproved",false));return rows;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReportPage reportPage(long cut,int page,int size){
        monitoring.finalReportCut(cut,false);
        if(page<0 || size<1 || size>100)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM school_final_report WHERE cut_id=?",Long.class,cut);
        long latest=jdbc.queryForObject("SELECT COALESCE(MAX(report_version),0) FROM school_final_report WHERE cut_id=?",Long.class,cut);
        var rows=tracking.jsonRows(projection()+" WHERE r.cut_id=? ORDER BY r.report_version DESC,r.id DESC LIMIT ? OFFSET ?",cut,size,(long)page*size);
        rows.forEach(row->row.put("formatPolicyApproved",false));
        return new ReportPage(rows,total,(total+size-1)/size,page,size,latest);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> get(long id){var rows=tracking.jsonRows(projection()+" WHERE r.id=? AND r.cut_id IS NOT NULL",id);if(rows.isEmpty())throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");var result=rows.get(0);var cut=monitoring.finalReportCut(((Number)result.get("cutId")).longValue(),false);
        result.putAll(tracking.jsonRows("SELECT transition_version AS \"transitionVersion\",published_at AS \"publishedAt\",finished_at AS \"finishedAt\",final_review_id AS \"finalReviewId\" FROM school_final_report WHERE id=?",id).get(0));
        result.put("cutStatus",cut.get("status"));
        // Published decisions use their frozen format, including when the current policy changes.
        var format=result.get("publishedAt")==null?formats.current():null;result.put("formatPolicyApproved",format!=null||result.get("publishedAt")!=null);result.put("currentFormat",format);
        long latest=jdbc.queryForObject("SELECT MAX(report_version) FROM school_final_report WHERE cut_id=?",Long.class,result.get("cutId"));
        boolean current=latest==((Number)result.get("version")).longValue()&&"CONSOLIDATED".equals(cut.get("status"));var permissions=monitoring.finalReportPermissions(cut);
        result.put("canPublish",current&&permissions.get("director")&&"DRAFT".equals(result.get("status")));
        var reviews=tracking.jsonRows(reviewProjection()+" WHERE v.report_id=? ORDER BY v.review_version DESC LIMIT 1",id);var latestReview=reviews.isEmpty()?null:reviewData(reviews.get(0));result.put("latestReview",latestReview);
        result.put("canReview",current&&permissions.get("rector")&&Set.of("PUBLISHED","OBSERVED","APPROVED").contains(result.get("status")));
        result.put("canFinish",current&&permissions.get("rector")&&"APPROVED".equals(result.get("status"))&&latestReview!=null&&"APPROVED".equals(latestReview.get("decision")));
        try{result.put("content",decode(jdbc.queryForObject("SELECT CAST(content_snapshot AS VARCHAR) FROM school_final_report WHERE id=?",String.class,id)));String frozen=jdbc.queryForObject("SELECT CAST(format_snapshot AS VARCHAR) FROM school_final_report WHERE id=?",String.class,id);result.put("publicationFormat",frozen==null?null:decode(frozen));}catch(Exception e){throw error(HttpStatus.CONFLICT,"SNAPSHOT_UNAVAILABLE");}return result;}
    private Map<?,?> decode(String json) throws Exception {var node=mapper.readTree(json);if(node.isTextual())node=mapper.readTree(node.asText());if(!node.isObject())throw new IllegalArgumentException();return mapper.convertValue(node,Map.class);}
    private String reviewProjection(){return "SELECT v.id,v.report_id AS \"reportId\",v.report_version AS \"reportVersion\",v.review_version AS \"reviewVersion\",v.status AS decision,v.observations AS comment,v.actor_id AS \"actorId\",p.name AS \"actorName\",v.reviewed_at AS \"reviewedAt\",CAST(v.evidence_urls AS VARCHAR) AS \"evidenceJson\" FROM rector_review v LEFT JOIN professor p ON p.id=v.actor_id";}
    private Map<String,Object> reviewData(Map<String,Object> row){var result=new LinkedHashMap<>(row);try{var node=mapper.readTree((String)result.remove("evidenceJson"));if(node.isTextual())node=mapper.readTree(node.asText());if(!node.isArray())throw new IllegalArgumentException();result.put("evidenceUrls",mapper.convertValue(node,List.class));}catch(Exception e){throw error(HttpStatus.CONFLICT,"SNAPSHOT_UNAVAILABLE");}return result;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReviewPage reviews(long id,int page,int size){get(id);if(page<0||size<1||size>100)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");long count=jdbc.queryForObject("SELECT COUNT(*) FROM rector_review WHERE report_id=?",Long.class,id);return new ReviewPage(tracking.jsonRows(reviewProjection()+" WHERE v.report_id=? ORDER BY v.review_version DESC,v.id DESC LIMIT ? OFFSET ?",id,size,(long)page*size).stream().map(this::reviewData).toList(),count,(count+size-1)/size,page,size);}
    private void commandKey(String value){required(value,"INVALID_REQUEST");if(value.length()>128)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");}
    private List<String> evidence(List<String> values){if(values==null||values.isEmpty()||values.size()>100)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"EVIDENCE_REQUIRED");var result=new ArrayList<String>();for(String value:values){try{if(value==null||value.length()>1000)throw new IllegalArgumentException();var uri=java.net.URI.create(value.trim());if(!Set.of("http","https").contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT))||uri.getHost()==null||uri.getRawUserInfo()!=null)throw new IllegalArgumentException();result.add(value.trim());}catch(IllegalArgumentException e){throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EVIDENCE_URL");}}if(new HashSet<>(result).size()!=result.size())throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");return List.copyOf(result);}
    private Map<String,Object> lockedForRector(long id){var existing=get(id);monitoring.rectorReportCut(((Number)existing.get("cutId")).longValue());jdbc.queryForList("SELECT id FROM school_final_report WHERE id=? FOR UPDATE",id);return get(id);}
    public ReviewReceipt review(long id,long expected,Review request){
        var before=lockedForRector(id);var actor=tracking.actor();commandKey(request.idempotencyKey());required(request.comment(),"COMMENT_REQUIRED");if(request.comment().length()>10000||!Set.of("APPROVED","OBSERVED").contains(String.valueOf(request.decision())))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");var supports=evidence(request.evidenceUrls());
        var prior=tracking.jsonRows(reviewProjection()+" WHERE v.report_id=? AND v.actor_id=? AND v.command_key=?",id,actor.id(),request.idempotencyKey());
        if(!prior.isEmpty()){var old=reviewData(prior.get(0));if(((Number)old.get("reportVersion")).longValue()!=request.reportVersion()||!Objects.equals(old.get("decision"),request.decision())||!Objects.equals(old.get("comment"),request.comment().trim())||!Objects.equals(old.get("evidenceUrls"),supports))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT");return new ReviewReceipt(old,before);}
        if(((Number)before.get("version")).longValue()!=request.reportVersion()||((Number)before.get("transitionVersion")).longValue()!=expected)throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        if(!Boolean.TRUE.equals(before.get("canReview")))throw error(HttpStatus.CONFLICT,"INVALID_TRANSITION");
        long next=jdbc.queryForObject("SELECT COALESCE(MAX(review_version),0)+1 FROM rector_review WHERE report_id=?",Long.class,id);String json;try{json=mapper.writeValueAsString(supports);}catch(Exception e){throw new IllegalStateException(e);}
        long reviewId=tracking.insert("rector_review",Map.of("monitoring_cut_id",before.get("cutId"),"report_id",id,"report_version",request.reportVersion(),"review_version",next,"status",request.decision(),"reviewed_by",Long.toString(actor.id()),"actor_id",actor.id(),"command_key",request.idempotencyKey(),"observations",request.comment().trim()));jdbc.update("UPDATE rector_review SET evidence_urls=CAST(? AS JSONB) WHERE id=?",json,reviewId);
        jdbc.update("UPDATE school_final_report SET status=?,transition_version=transition_version+1 WHERE id=?",request.decision(),id);var saved=reviewData(tracking.jsonRows(reviewProjection()+" WHERE v.id=?",reviewId).get(0));audit.record(AuditAction.CREATE,"RECTOR_REVIEW",reviewId,"Revisión de versión de informe con soportes",Map.of("reportId",id,"status",before.get("status")),Map.of("reportId",id,"reportVersion",request.reportVersion(),"decision",request.decision()));return new ReviewReceipt(saved,get(id));
    }
    public Map<String,Object> finish(long id,long expected,Finish request){
        var before=lockedForRector(id);commandKey(request.idempotencyKey());if(!request.confirmed())throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        var stored=jdbc.queryForMap("SELECT finish_key,finished_by,final_review_id FROM school_final_report WHERE id=?",id);
        if(stored.get("finish_key")!=null){if(!Objects.equals(stored.get("finish_key"),request.idempotencyKey())||((Number)stored.get("finished_by")).longValue()!=tracking.actor().id()||((Number)stored.get("final_review_id")).longValue()!=request.reviewId()||((Number)before.get("version")).longValue()!=request.reportVersion())throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT");return before;}
        if(((Number)before.get("version")).longValue()!=request.reportVersion()||((Number)before.get("transitionVersion")).longValue()!=expected)throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        @SuppressWarnings("unchecked") var latest=(Map<String,Object>)before.get("latestReview");if(!Boolean.TRUE.equals(before.get("canFinish"))||latest==null||((Number)latest.get("id")).longValue()!=request.reviewId()||((Number)latest.get("reportVersion")).longValue()!=request.reportVersion())throw error(HttpStatus.CONFLICT,"APPROVED_REVIEW_REQUIRED");
        jdbc.update("UPDATE school_final_report SET finished_at=CURRENT_TIMESTAMP,finished_by=?,finish_key=?,final_review_id=?,transition_version=transition_version+1 WHERE id=?",tracking.actor().id(),request.idempotencyKey(),request.reviewId(),id);
        jdbc.update("UPDATE monitoring_cut SET status='FIN',version=version+1 WHERE id=?",before.get("cutId"));audit.record(AuditAction.STATUS_CHANGE,"MONITORING_CUT",((Number)before.get("cutId")).longValue(),"Fin tras aprobación rectoral de versión vigente",Map.of("status","CONSOLIDATED"),Map.of("status","FIN","reportId",id,"reportVersion",request.reportVersion(),"reviewId",request.reviewId()));return get(id);
    }
    public Map<String,Object> publish(long id,long expected,Publish r){
        var before=get(id);var cut=monitoring.finalReportCut(((Number)before.get("cutId")).longValue(),true);
        jdbc.queryForList("SELECT id FROM school_final_report WHERE id=? FOR UPDATE",id);
        required(r.idempotencyKey(),"INVALID_REQUEST");if(r.idempotencyKey().length()>128||!r.confirmed())throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        var stored=jdbc.queryForMap("SELECT publication_key,published_by,format_policy_id,transition_version FROM school_final_report WHERE id=?",id);
        if(stored.get("publication_key")!=null){
            if(!r.idempotencyKey().equals(stored.get("publication_key"))||!Objects.equals(((Number)stored.get("published_by")).longValue(),tracking.actor().id())||jdbc.queryForObject("SELECT version FROM institutional_policy WHERE id=?",Long.class,stored.get("format_policy_id"))!=r.formatVersion())throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT");return get(id);
        }
        if(((Number)stored.get("transition_version")).longValue()!=expected)throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        long latest=jdbc.queryForObject("SELECT MAX(report_version) FROM school_final_report WHERE cut_id=?",Long.class,before.get("cutId"));
        if(!"CONSOLIDATED".equals(cut.get("status"))||!"DRAFT".equals(before.get("status"))||latest!=((Number)before.get("version")).longValue())throw error(HttpStatus.CONFLICT,"INVALID_TRANSITION");
        formats.lock();var format=formats.current();if(format==null)throw error(HttpStatus.CONFLICT,"POLICY_UNSET");if(format.version()!=r.formatVersion())throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        @SuppressWarnings("unchecked") var content=(Map<String,Object>)before.get("content");
        for(String section:format.requiredSections()){
            Object value=switch(section){case "TITLE"->before.get("title");case "ACHIEVEMENTS"->before.get("achievementsSummary");case "DIFFICULTIES"->before.get("difficultiesSummary");case "CONCLUSIONS"->before.get("conclusions");case "CUT","STRATEGIC_RESULTS"->content.get("cutReport");case "BRANCH_RESULTS"->content.get("branchResults");case "SITUATIONS","CORRECTIVES"->content.get("situations");default->null;};
            if(value==null||(value instanceof String text&&text.isBlank()))throw error(HttpStatus.CONFLICT,"INVALID_REPORT_FORMAT");
        }
        String frozen;try{frozen=mapper.writeValueAsString(format);}catch(Exception e){throw new IllegalStateException(e);}
        jdbc.update("UPDATE school_final_report SET status='PUBLISHED',transition_version=transition_version+1,published_at=CURRENT_TIMESTAMP,published_by=?,publication_key=?,format_policy_id=?,format_snapshot=CAST(? AS JSONB) WHERE id=?",tracking.actor().id(),r.idempotencyKey(),format.id(),frozen,id);
        audit.record(AuditAction.STATUS_CHANGE,"SCHOOL_FINAL_REPORT",id,"Informe publicado para consulta institucional",Map.of("status","DRAFT"),Map.of("status","PUBLISHED","reportVersion",latest,"formatVersion",format.version()));return get(id);
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> create(Draft r,long expected){
        var cut=monitoring.finalReportCut(r.cutId(),true);required(r.title(),"NAME_REQUIRED");required(r.achievementsSummary(),"DESCRIPTION_REQUIRED");required(r.difficultiesSummary(),"DESCRIPTION_REQUIRED");required(r.conclusions(),"DESCRIPTION_REQUIRED");
        if(r.title().length()>255 || r.idempotencyKey()==null || r.idempotencyKey().isBlank() || r.idempotencyKey().length()>128)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        var prior=tracking.jsonRows(projection()+" WHERE r.cut_id=? AND r.author_id=? AND r.creation_key=?",r.cutId(),Long.toString(tracking.actor().id()),r.idempotencyKey());
        if(!prior.isEmpty()){var row=prior.get(0);if(!Objects.equals(row.get("title"),r.title().trim()) || !Objects.equals(row.get("achievementsSummary"),r.achievementsSummary().trim()) || !Objects.equals(row.get("difficultiesSummary"),r.difficultiesSummary().trim()) || !Objects.equals(row.get("conclusions"),r.conclusions().trim()))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT");return get(((Number)row.get("id")).longValue());}
        long latest=jdbc.queryForObject("SELECT COALESCE(MAX(report_version),0) FROM school_final_report WHERE cut_id=?",Long.class,r.cutId());if(latest!=expected)throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
        var branches=monitoring.branches(r.cutId());if(!"CONSOLIDATED".equals(cut.get("status")) || branches.size()!=4 || branches.stream().anyMatch(b->!Set.of("SUBMITTED","REVIEWED","CLOSED").contains(b.get("status"))))throw error(HttpStatus.CONFLICT,"BRANCH_INCOMPLETE");
        var branchResults=new ArrayList<Map<String,Object>>();var situations=new ArrayList<Map<String,Object>>();
        for(var branch:branches){long id=((Number)branch.get("id")).longValue();var entries=new ArrayList<Map<String,Object>>();for(int page=0;;page++){var batch=monitoring.entryPage(id,page,100);entries.addAll(batch.items());if(page+1>=batch.totalPages())break;}if(entries.isEmpty())throw error(HttpStatus.CONFLICT,"BRANCH_INCOMPLETE");
            var bundle=new LinkedHashMap<String,Object>();bundle.put("branch",branch);bundle.put("entries",entries);
            if("DEPARTMENT".equals(branch.get("branchType"))){var results=monitoring.departmentResults(id);if(results.isEmpty() || results.stream().anyMatch(d->d.get("submittedAt")==null))throw error(HttpStatus.CONFLICT,"BRANCH_INCOMPLETE");bundle.put("departmentResults",results);var histories=new ArrayList<Map<String,Object>>();for(var result:results){long department=((Number)result.get("departmentId")).longValue();var deliveries=new ArrayList<Map<String,Object>>();for(int page=0;;page++){var batch=monitoring.departmentDeliveryPage(id,department,page,100);deliveries.addAll(batch.items());if(page+1>=batch.totalPages())break;}histories.add(Map.of("departmentId",department,"departmentName",result.get("departmentName"),"deliveries",deliveries));}bundle.put("departmentDeliveryHistories",histories);}
            branchResults.add(bundle);for(var entry:entries){@SuppressWarnings("unchecked") var findings=(List<Map<String,Object>>)entry.get("situations");for(var finding:findings){long findingId=((Number)finding.get("id")).longValue();var detail=monitoring.situationForReport(findingId);detail.put("correctiveActions",monitoring.correctiveActionsForReport(findingId));situations.add(detail);}}
        }
        var content=new LinkedHashMap<String,Object>();content.put("cutReport",monitoring.report(r.cutId(),null));content.put("branchResults",branchResults);content.put("situations",situations);content.put("assembledAt",jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",java.sql.Timestamp.class).toInstant().toString());
        String json;try{json=mapper.writeValueAsString(content);}catch(Exception e){throw new IllegalStateException(e);}
        long id=tracking.insert("school_final_report",Map.of("cut_id",r.cutId(),"cycle_id",cut.get("cycleId"),"title",r.title().trim(),"executive_summary",r.conclusions().trim(),"achievements_summary",r.achievementsSummary().trim(),"challenges_summary",r.difficultiesSummary().trim(),"conclusions",r.conclusions().trim(),"author_id",Long.toString(tracking.actor().id()),"report_version",latest+1,"creation_key",r.idempotencyKey()));
        jdbc.update("UPDATE school_final_report SET content_snapshot=CAST(? AS JSONB) WHERE id=?",json,id);var after=get(id);audit.record(AuditAction.CREATE,"SCHOOL_FINAL_REPORT",id,"Borrador versionado de informe de Escuela",null,Map.of("cutId",r.cutId(),"version",latest+1,"status","DRAFT"));return after;
    }
}
