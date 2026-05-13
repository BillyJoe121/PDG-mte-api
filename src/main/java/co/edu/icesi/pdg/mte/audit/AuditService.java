package co.edu.icesi.pdg.mte.audit;

import co.edu.icesi.pdg.mte.api.dto.AuditDtos;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AuditService {
    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(AuditAction action, String entityType, Object entityId, String summary, Object before, Object after) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(String.valueOf(entityId));
        log.setSummary(summary);
        log.setBeforeSnapshot(snapshot(before));
        log.setAfterSnapshot(snapshot(after));
        applyActor(log);
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditDtos.AuditLogResponse> list(AuditAction action, String entityType, String entityId) {
        Specification<AuditLog> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null) {
                predicates.add(builder.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(builder.equal(builder.upper(root.get("entityType")), entityType.trim().toUpperCase()));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(builder.equal(root.get("entityId"), entityId.trim()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(specification, Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void applyActor(AuditLog log) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ExternalUserContext context)) {
            return;
        }
        log.setActorExternalUserId(context.externalUserId());
        log.setActorUsername(context.username());
        log.setActorRoles(String.join(",", context.roles()));
    }

    private String snapshot(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }

    private AuditDtos.AuditLogResponse toResponse(AuditLog log) {
        return new AuditDtos.AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getSummary(),
                log.getActorExternalUserId(),
                log.getActorUsername(),
                log.getActorRoles(),
                log.getBeforeSnapshot(),
                log.getAfterSnapshot(),
                log.getCreatedAt()
        );
    }
}
