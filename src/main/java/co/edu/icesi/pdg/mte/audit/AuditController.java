package co.edu.icesi.pdg.mte.audit;

import co.edu.icesi.pdg.mte.api.dto.AuditDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditDtos.AuditLogResponse> list(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId
    ) {
        return auditService.list(action, entityType, entityId);
    }
}
