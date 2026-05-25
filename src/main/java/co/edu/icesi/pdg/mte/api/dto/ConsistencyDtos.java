package co.edu.icesi.pdg.mte.api.dto;

import java.time.Instant;
import java.util.List;

public final class ConsistencyDtos {
    private ConsistencyDtos() {
    }

    public enum ConsistencySeverity {
        ALTA,
        MEDIA,
        BAJA
    }

    public enum ConsistencyModule {
        OKRS,
        INDICADORES,
        PROYECTOS
    }

    public enum ConsistencyFindingType {
        KR_WITHOUT_ACTIVE_PROJECTS,
        ACTIVE_PROJECT_WITHOUT_KR,
        ACTIVE_PROJECT_WITHOUT_RECENT_PROGRESS
    }

    public enum ConsistencyEntityType {
        KEY_RESULT,
        PROJECT
    }

    public record ConsistencySummaryResponse(
            long total,
            long high,
            long medium,
            long low
    ) {
    }

    public record ConsistencyFindingResponse(
            String id,
            ConsistencyFindingType type,
            ConsistencySeverity severity,
            ConsistencyModule module,
            ConsistencyEntityType entityType,
            Long entityId,
            String entityCode,
            String entityName,
            String description,
            String recommendedAction,
            String actionLabel,
            String actionUrl,
            Instant detectedAt
    ) {
    }

    public record ConsistencyCheckResponse(
            ConsistencySummaryResponse summary,
            List<ConsistencyFindingResponse> findings
    ) {
    }
}
