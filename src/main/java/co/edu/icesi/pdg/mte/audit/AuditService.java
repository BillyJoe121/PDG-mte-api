package co.edu.icesi.pdg.mte.audit;

import co.edu.icesi.pdg.mte.api.dto.AuditDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public List<AuditDtos.AuditLogResponse> list(
            AuditAction action,
            String entityType,
            String entityId,
            String actorUsername,
            Instant from,
            Instant to
    ) {
        validateRange(from, to);
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
            if (actorUsername != null && !actorUsername.isBlank()) {
                predicates.add(builder.equal(
                        builder.upper(root.get("actorUsername")),
                        actorUsername.trim().toUpperCase()
                ));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(specification, Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AuditDtos.AuditSummaryResponse summary(
            AuditAction action,
            String entityType,
            String entityId,
            String actorUsername,
            Instant from,
            Instant to
    ) {
        List<AuditDtos.AuditLogResponse> logs = list(action, entityType, entityId, actorUsername, from, to);
        return new AuditDtos.AuditSummaryResponse(
                logs.size(),
                counts(logs, log -> log.action().name()),
                counts(logs, AuditDtos.AuditLogResponse::entityType),
                counts(logs, log -> log.actorUsername() == null || log.actorUsername().isBlank() ? "SISTEMA" : log.actorUsername()),
                trend(logs, from, to)
        );
    }

    private List<AuditDtos.AuditCountResponse> counts(
            List<AuditDtos.AuditLogResponse> logs,
            Function<AuditDtos.AuditLogResponse, String> classifier
    ) {
        return logs.stream()
                .collect(Collectors.groupingBy(classifier, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new AuditDtos.AuditCountResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<AuditDtos.AuditTrendPointResponse> trend(
            List<AuditDtos.AuditLogResponse> logs,
            Instant from,
            Instant to
    ) {
        if (logs.isEmpty() && (from == null || to == null)) {
            return List.of();
        }
        Map<LocalDate, Long> countsByDay = logs.stream()
                .filter(log -> log.createdAt() != null)
                .collect(Collectors.groupingBy(
                        log -> log.createdAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        LocalDate first = from != null
                ? from.atZone(ZoneOffset.UTC).toLocalDate()
                : countsByDay.keySet().stream().min(Comparator.naturalOrder()).orElse(null);
        LocalDate last = to != null
                ? to.atZone(ZoneOffset.UTC).toLocalDate()
                : countsByDay.keySet().stream().max(Comparator.naturalOrder()).orElse(null);
        if (first == null || last == null) {
            return List.of();
        }
        return first.datesUntil(last.plusDays(1))
                .map(date -> new AuditDtos.AuditTrendPointResponse(date, countsByDay.getOrDefault(date, 0L)))
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

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El rango de fechas de auditoria es invalido.");
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
