package co.edu.icesi.pdg.mte.governance;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Service.error;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class PlannerReferencesV2Service {
    public record Page(List<Map<String,Object>> items,long totalElements,long totalPages,int currentPage,int pageSize,String source) {}
    private final JdbcTemplate jdbc;
    private final TrackingV2Service tracking;
    public PlannerReferencesV2Service(JdbcTemplate jdbc,TrackingV2Service tracking) { this.jdbc=jdbc;this.tracking=tracking; }

    public Page list(long initiativeId,int page,int size) {
        if(page<0 || page>1000000 || size<1 || size>100) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PAGINATION");
        tracking.initiative(initiativeId,tracking.actor(),false);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM planner_task_ref WHERE initiative_id=?",Long.class,initiativeId);
        var items=tracking.jsonRows("SELECT t.id,t.initiative_id AS \"initiativeId\",t.provider,t.external_plan_id AS \"externalPlanId\",t.external_task_id AS \"externalTaskId\",t.external_url AS \"externalUrl\",t.title_snapshot AS \"titleSnapshot\",t.bucket_snapshot AS \"bucketSnapshot\",t.status_snapshot AS \"statusSnapshot\",t.due_date_snapshot AS \"dueDateSnapshot\",t.last_synced_at AS \"lastSyncedAt\",CASE WHEN c.revoked_at IS NOT NULL THEN 'REVOKED' WHEN c.consent_status='ACTIVE' THEN 'ACTIVE' ELSE 'UNAVAILABLE' END AS \"connectionState\" FROM planner_task_ref t JOIN planner_connection c ON c.id=t.connection_id WHERE t.initiative_id=? ORDER BY t.id DESC LIMIT ? OFFSET ?",initiativeId,size,(long)page*size);
        return new Page(items,total,(total+size-1)/size,page,size,"REGISTERED_REFERENCES");
    }
}
