package co.edu.icesi.pdg.mte.api.dto;

import co.edu.icesi.pdg.mte.project.ProjectOrigin;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectSyncStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProjectDtos {
    private ProjectDtos() {
    }

    public record ProjectRequest(
            @NotBlank String name,
            @NotBlank String description,
            @NotNull ProjectType type,
            @NotNull Long departmentId,
            ProjectStatus status,
            @NotBlank @Pattern(regexp = "^\\d{4}-(Q[1-4]|[1-2])$") String startPeriod,
            @Pattern(regexp = "^\\d{4}-(Q[1-4]|[1-2])$") String endPeriod,
            LocalDate startDate,
            LocalDate endDate,
            List<@NotBlank String> tutors
    ) {
    }

    public record ProjectUpdateRequest(
            @NotBlank String name,
            @NotBlank String description,
            @NotNull ProjectType type,
            @NotNull Long departmentId,
            @NotBlank @Pattern(regexp = "^\\d{4}-(Q[1-4]|[1-2])$") String startPeriod,
            @Pattern(regexp = "^\\d{4}-(Q[1-4]|[1-2])$") String endPeriod,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate actualEndDate,
            List<@NotBlank String> tutors
    ) {
    }

    public record ProjectStatusRequest(
            @NotNull ProjectStatus status
    ) {
    }

    public record ProjectProgressRequest(
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal progressPercent,
            @NotBlank String comment,
            String milestones
    ) {
    }

    public record ProjectResponse(
            Long id,
            Long externalProjectId,
            String externalSource,
            String name,
            String description,
            ProjectType type,
            Long departmentId,
            String departmentName,
            ProjectStatus status,
            String startPeriod,
            String endPeriod,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate actualEndDate,
            BigDecimal globalProgress,
            List<String> tutors,
            ProjectOrigin origin,
            ProjectSyncStatus syncStatus,
            Instant lastSyncedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ProjectProgressResponse(
            Long id,
            Long projectId,
            BigDecimal progressPercent,
            String comment,
            String milestones,
            Long createdByExternalUserId,
            Instant createdAt
    ) {
    }

    public record ProjectDetailResponse(
            ProjectResponse project,
            ProjectKpiResponse kpis,
            List<ProjectProgressResponse> history,
            List<ProjectKeyResultLinkResponse> linkedKeyResults,
            ImpactChainResponse contributionChain
    ) {
    }

    public record ProjectKpiResponse(
            int progressEntries,
            int linkedKeyResults,
            BigDecimal declaredContributionWeight,
            BigDecimal appliedContribution,
            boolean completed,
            boolean overweightWarning
    ) {
    }

    public record ProjectSyncResponse(
            int imported,
            int updated,
            int failed,
            List<String> warnings
    ) {
    }

    public record ProjectKeyResultLinkRequest(
            @NotNull Long projectId,
            @NotNull Long keyResultId,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal contributionWeight
    ) {
    }

    public record ProjectKeyResultLinkResponse(
            Long id,
            Long projectId,
            String projectName,
            Long keyResultId,
            String keyResultDescription,
            BigDecimal contributionWeight,
            BigDecimal totalWeightForKeyResult,
            boolean overweightWarning,
            boolean active,
            Instant createdAt
    ) {
    }

    public record ImpactChainResponse(
            Long projectId,
            String projectName,
            BigDecimal globalProgress,
            ProjectStatus status,
            List<ImpactChainItemResponse> impacts
    ) {
    }

    public record ImpactChainItemResponse(
            Long linkId,
            Long keyResultId,
            String keyResultDescription,
            Long objectiveId,
            String objectiveName,
            BigDecimal contributionWeight,
            BigDecimal appliedContribution,
            boolean projectCompleted,
            String period
    ) {
    }
}
