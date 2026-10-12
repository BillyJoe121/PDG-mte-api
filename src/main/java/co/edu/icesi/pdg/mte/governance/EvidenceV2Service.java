package co.edu.icesi.pdg.mte.governance;

import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.net.URI;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Service.error;

@Service
@Transactional
public class EvidenceV2Service {
    public record Write(String title, String evidenceType, String externalRef, String fileHashSha256) {}
    public record Page(List<Map<String,Object>> items,long totalElements,long totalPages,int currentPage,int pageSize) {}
    private final JdbcTemplate jdbc;
    private final TrackingV2Service tracking;
    private final AuditService audit;
    public EvidenceV2Service(JdbcTemplate jdbc, TrackingV2Service tracking, AuditService audit) {
        this.jdbc=jdbc; this.tracking=tracking; this.audit=audit;
    }
    private String column(String owner) {
        return switch(owner) { case "measurement" -> "measurement_id"; case "initiative" -> "initiative_id";
            default -> throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST"); };
    }
    private void authorize(String owner,long id,boolean write) {
        var actor=tracking.actor();
        if (owner.equals("initiative")) { tracking.initiative(id,actor,write); return; }
        column(owner);
        var rows=jdbc.queryForList("SELECT key_result_id, status FROM kr_measurement WHERE id=?",id);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"OUT_OF_SCOPE");
        tracking.scopedKr(((Number)rows.get(0).get("key_result_id")).longValue(),actor,write);
        if(write && !"ACTIVE".equals(rows.get(0).get("status"))) throw error(HttpStatus.CONFLICT,"INACTIVE_RESOURCE");
    }
    private String projection() {
        return "SELECT id, title, evidence_type AS \"evidenceType\", external_ref AS \"externalRef\", file_hash_sha256 AS \"fileHashSha256\", active, created_by AS \"createdBy\", created_at AS \"createdAt\", measurement_id AS \"measurementId\", initiative_id AS \"initiativeId\" FROM evidence";
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(String owner,long id) {
        authorize(owner,id,false);
        return tracking.jsonRows(projection()+" WHERE "+column(owner)+"=? ORDER BY created_at DESC,id DESC LIMIT 100",id);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page search(String owner,long id,int page,int size,String evidenceType,Boolean active) {
        if(page<0 || page>1000000 || size<1 || size>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        if(evidenceType!=null && !Set.of("DOCUMENT","URL","PLANNER_TASK_REF","REPOSITORY").contains(evidenceType)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EVIDENCE");
        authorize(owner,id,false);
        String where=" WHERE "+column(owner)+"=?";var params=new ArrayList<Object>();params.add(id);
        if(evidenceType!=null){where+=" AND evidence_type=?";params.add(evidenceType);}
        if(active!=null){where+=" AND active=?";params.add(active);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM evidence"+where,Long.class,params.toArray());
        params.add(size);params.add((long)page*size);
        var items=tracking.jsonRows(projection()+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",params.toArray());
        return new Page(items,total,(total+size-1)/size,page,size);
    }
    public Map<String,Object> create(String owner,long id,Write r) {
        authorize(owner,id,true);
        GovernanceV2Service.required(r.title(),"NAME_REQUIRED");
        if(r.title().length()>255 || r.evidenceType()==null || !Set.of("DOCUMENT","URL","PLANNER_TASK_REF","REPOSITORY").contains(r.evidenceType()))
            throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EVIDENCE");
        String ref=r.externalRef()==null ? "" : r.externalRef().trim();
        try {
            URI uri=URI.create(ref);
            if(ref.length()>1000 || !Set.of("https","http").contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT)) || uri.getHost()==null || uri.getRawUserInfo()!=null)
                throw new IllegalArgumentException();
        } catch(IllegalArgumentException e) { throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EVIDENCE_URL"); }
        String hash=r.fileHashSha256()==null || r.fileHashSha256().isBlank() ? null : r.fileHashSha256().trim().toLowerCase(Locale.ROOT);
        if(hash!=null && !hash.matches("[a-f0-9]{64}")) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EVIDENCE_HASH");
        Map<String,Object> values=new HashMap<>();
        values.put(column(owner),id); values.put("title",r.title().trim()); values.put("evidence_type",r.evidenceType());
        values.put("external_ref",ref); values.put("file_hash_sha256",hash); values.put("created_by",tracking.actor().name());
        long entry=tracking.insert("evidence",values);
        var result=tracking.jsonRows(projection()+" WHERE id=?",entry).get(0);
        audit.record(AuditAction.CREATE,"EVIDENCE",entry,"Referencia de evidencia",null,result);
        return result;
    }
}
