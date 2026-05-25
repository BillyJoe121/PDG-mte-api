package co.edu.icesi.pdg.mte.audit;

import co.edu.icesi.pdg.mte.api.dto.AuditDtos;
import co.edu.icesi.pdg.mte.api.dto.PageDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.common.Pagination;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.Instant;
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
    public List<AuditDtos.AuditLogResponse> list(AuditAction action, String entityType, String entityId, Instant from, Instant to) {
        validateRange(from, to);
        return repository.findAll(specification(action, entityType, entityId, from, to), Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageDtos.PageResponse<AuditDtos.AuditLogResponse> listPage(
            AuditAction action,
            String entityType,
            String entityId,
            Instant from,
            Instant to,
            Integer page,
            Integer size
    ) {
        validateRange(from, to);
        PageRequest pageRequest = Pagination.pageRequest(page, size, 50, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<AuditLog> logs = repository.findAll(specification(action, entityType, entityId, from, to), pageRequest);
        return new PageDtos.PageResponse<>(
                logs.getContent().stream().map(this::toResponse).toList(),
                logs.getNumber(),
                logs.getSize(),
                logs.getTotalElements(),
                logs.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AuditDtos.AuditSummaryResponse summary(AuditAction action, String entityType, String entityId, Instant from, Instant to) {
        List<AuditDtos.AuditLogResponse> logs = list(action, entityType, entityId, from, to);
        List<AuditDtos.AuditCountResponse> byAction = logs.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        log -> log.action().name(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ))
                .entrySet()
                .stream()
                .map(entry -> new AuditDtos.AuditCountResponse(entry.getKey(), entry.getValue()))
                .toList();
        List<AuditDtos.AuditCountResponse> byEntityType = logs.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        AuditDtos.AuditLogResponse::entityType,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ))
                .entrySet()
                .stream()
                .map(entry -> new AuditDtos.AuditCountResponse(entry.getKey(), entry.getValue()))
                .toList();
        return new AuditDtos.AuditSummaryResponse(logs.size(), byAction, byEntityType);
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

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El rango de fechas de auditoria es invalido.");
        }
    }

    private Specification<AuditLog> specification(AuditAction action, String entityType, String entityId, Instant from, Instant to) {
        return (root, query, builder) -> {
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
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
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
