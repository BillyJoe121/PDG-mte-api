package co.edu.icesi.pdg.mte.integration;

import java.time.LocalDate;
import java.util.List;

public record ExternalProjectPayload(
        Long externalProjectId,
        String name,
        String description,
        String status,
        String type,
        String departmentName,
        String startPeriod,
        String endPeriod,
        LocalDate startDate,
        LocalDate endDate,
        List<String> tutors,
        String rawPayload
) {
}
