package co.edu.icesi.pdg.mte.api.dto;

import co.edu.icesi.pdg.mte.audit.AuditAction;

import java.time.Instant;

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
}
