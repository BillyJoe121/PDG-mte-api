package co.edu.icesi.pdg.mte.api.dto;

import co.edu.icesi.pdg.mte.audit.AuditAction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AuditDtos {
    private AuditDtos() {
    }

    public record AuditLogResponse(
            Long id,
            AuditAction action,
            String entityType,
            String entityId,
            String summary,
            Long actorExternalUserId,
            String actorUsername,
            String actorRoles,
            String beforeSnapshot,
            String afterSnapshot,
            Instant createdAt
    ) {
    }

    public record AuditSummaryResponse(
            long totalEvents,
            List<AuditCountResponse> byAction,
            List<AuditCountResponse> byEntityType,
            List<AuditCountResponse> byActor,
            List<AuditTrendPointResponse> trend
    ) {
    }

    public record AuditCountResponse(
            String key,
            long count
    ) {
    }

    public record AuditTrendPointResponse(
            LocalDate date,
            long count
    ) {
    }
}
