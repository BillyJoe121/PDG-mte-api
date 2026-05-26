package co.edu.icesi.pdg.mte.api.dto;

import co.edu.icesi.pdg.mte.project.ProjectOrigin;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectSyncStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import co.edu.icesi.pdg.mte.integration.ContributionType;
import jakarta.validation.Valid;
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
            Long keyResultId,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal contributionWeight,
            String linkStatus,
            String jiraKey,
            List<@NotBlank String> tutors,
            List<@Valid ProjectKeyResultDraftRequest> keyResultLinks
    ) {
        public ProjectRequest(
                String name,
                String description,
                ProjectType type,
                Long departmentId,
                ProjectStatus status,
                String startPeriod,
                String endPeriod,
                LocalDate startDate,
                LocalDate endDate,
                List<String> tutors,
                List<ProjectKeyResultDraftRequest> keyResultLinks
        ) {
            this(name, description, type, departmentId, status, startPeriod, endPeriod, startDate, endDate,
                    null, null, null, null, tutors, keyResultLinks);
        }
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
            Long keyResultId,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal contributionWeight,
            String linkStatus,
            String jiraKey,
            List<@NotBlank String> tutors
    ) {
        public ProjectUpdateRequest(
                String name,
                String description,
                ProjectType type,
                Long departmentId,
                String startPeriod,
                String endPeriod,
                LocalDate startDate,
                LocalDate endDate,
                LocalDate actualEndDate,
                List<String> tutors
        ) {
            this(name, description, type, departmentId, startPeriod, endPeriod, startDate, endDate, actualEndDate,
                    null, null, null, null, tutors);
        }
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
            Long keyResultId,
            String keyResultName,
            BigDecimal contributionWeight,
            String linkStatus,
            String jiraKey,
            List<String> tutors,
            List<ProjectLinkedKeyResultResponse> linkedKeyResults,
            ProjectOrigin origin,
            ProjectSyncStatus syncStatus,
            Instant lastSyncedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public ProjectResponse(
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
                List<ProjectLinkedKeyResultResponse> linkedKeyResults,
                ProjectOrigin origin,
                ProjectSyncStatus syncStatus,
                Instant lastSyncedAt,
                Instant createdAt,
                Instant updatedAt
        ) {
            this(id, externalProjectId, externalSource, name, description, type, departmentId, departmentName, status,
                    startPeriod, endPeriod, startDate, endDate, actualEndDate, globalProgress, null, null, null, null,
                    null, tutors, linkedKeyResults, origin, syncStatus, lastSyncedAt, createdAt, updatedAt);
        }
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
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal contributionWeight,
            @NotNull ContributionType contributionType
    ) {
    }

    public record ProjectKeyResultDraftRequest(
            @NotNull Long keyResultId,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal contributionWeight,
            @NotNull ContributionType contributionType
    ) {
    }

    public record ProjectKeyResultLinkResponse(
            Long id,
            Long projectId,
            String projectName,
            Long keyResultId,
            String keyResultDescription,
            BigDecimal contributionWeight,
            ContributionType contributionType,
            BigDecimal totalWeightForKeyResult,
            boolean overweightWarning,
            boolean active,
            Instant createdAt
    ) {
    }

    public record ProjectLinkedKeyResultResponse(
            Long linkId,
            Long keyResultId,
            String keyResultName,
            String keyResultDescription,
            BigDecimal contributionWeight,
            ContributionType contributionType,
            boolean active
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
            ContributionType contributionType,
            BigDecimal appliedContribution,
            boolean projectCompleted,
            String period
    ) {
    }

    public record ProjectTeacherRequest(
            @NotNull Long teacherId,
            @NotNull Long roleId,
            @NotNull LocalDate joinedAt,
            LocalDate leftAt
    ) {
    }

    public record ProjectTeacherResponse(
            Long projectId,
            String projectName,
            Long teacherId,
            String teacherName,
            Long roleId,
            String roleName,
            LocalDate joinedAt,
            LocalDate leftAt
    ) {
    }
}
