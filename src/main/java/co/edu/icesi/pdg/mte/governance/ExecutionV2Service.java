package co.edu.icesi.pdg.mte.governance;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Service.error;

@Service
@Transactional
public class ExecutionV2Service {
    public record Write(LocalDate executionDate,String summary,String notes) {}
    public record Page(List<Map<String,Object>> items,long totalElements,long totalPages,int currentPage,int pageSize) {}
    private final JdbcTemplate jdbc;
    private final TrackingV2Service tracking;
    private final AuditService audit;
    public ExecutionV2Service(JdbcTemplate jdbc,TrackingV2Service tracking,AuditService audit) { this.jdbc=jdbc; this.tracking=tracking; this.audit=audit; }
    private String projection() {
        return "SELECT e.id,e.initiative_id AS \"initiativeId\",e.execution_date AS \"executionDate\",e.summary,e.notes,e.created_at AS \"createdAt\",p.name AS \"actorName\" FROM initiative_execution_log e JOIN professor p ON p.id=e.actor_id";
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(long id) {
        tracking.initiative(id,tracking.actor(),false);
        return tracking.jsonRows(projection()+" WHERE e.initiative_id=? ORDER BY e.execution_date DESC,e.id DESC LIMIT 100",id);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page search(long id,int page,int size) {
        if(page<0 || page>1000000 || size<1 || size>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        tracking.initiative(id,tracking.actor(),false);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM initiative_execution_log WHERE initiative_id=?",Long.class,id);
        var items=tracking.jsonRows(projection()+" WHERE e.initiative_id=? ORDER BY e.execution_date DESC,e.id DESC LIMIT ? OFFSET ?",id,size,(long)page*size);
        return new Page(items,total,(total+size-1)/size,page,size);
    }
    public Map<String,Object> create(long id,Write r) {
        var actor=tracking.actor(); tracking.initiative(id,actor,true);
        if(r.executionDate()==null || r.executionDate().isAfter(LocalDate.now(ZoneId.of("America/Bogota")))) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EXECUTION_DATE");
        GovernanceV2Service.required(r.summary(),"SUMMARY_REQUIRED");
        if(r.notes()!=null && r.notes().length()>10000) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REQUEST");
        Map<String,Object> values=new HashMap<>(); values.put("initiative_id",id); values.put("actor_id",actor.id());
        values.put("execution_date",java.sql.Date.valueOf(r.executionDate())); values.put("summary",r.summary().trim()); values.put("notes",r.notes());
        long entry=tracking.insert("initiative_execution_log",values);
        var result=tracking.jsonRows(projection()+" WHERE e.id=?",entry).get(0);
        audit.record(AuditAction.CREATE,"INITIATIVE_EXECUTION",entry,"Registro de ejecución de iniciativa #"+id,null,result);
        return result;
    }
}
