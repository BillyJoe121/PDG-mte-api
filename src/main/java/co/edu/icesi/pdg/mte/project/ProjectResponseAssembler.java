package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
class ProjectResponseAssembler {
    private final ProjectKeyResultLinkRepository linkRepository;

    ProjectResponseAssembler(ProjectKeyResultLinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    ProjectDtos.ProjectKpiResponse kpis(
            Project project,
            List<ProjectDtos.ProjectProgressResponse> history,
            List<ProjectKeyResultLink> links
    ) {
        BigDecimal declaredContribution = links.stream()
                .map(ProjectKeyResultLink::getContributionWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal appliedContribution = project.getStatus() == ProjectStatus.FINALIZADO
                ? declaredContribution
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return new ProjectDtos.ProjectKpiResponse(
                history.size(),
                links.size(),
                declaredContribution,
                appliedContribution,
                project.getStatus() == ProjectStatus.FINALIZADO,
                declaredContribution.compareTo(BigDecimal.valueOf(100)) > 0
        );
    }

    ProjectDtos.ImpactChainResponse contributionChain(Project project, List<ProjectKeyResultLink> links) {
        return new ProjectDtos.ImpactChainResponse(
                project.getId(),
                project.getName(),
                project.getGlobalProgress(),
                project.getStatus(),
                links.stream().map(this::toImpactItem).toList()
        );
    }

    ProjectDtos.ProjectKeyResultLinkResponse toLinkResponse(ProjectKeyResultLink link) {
        return toLinkResponse(link, totalWeightForKeyResult(link.getKeyResult().getId()));
    }

    List<ProjectDtos.ProjectKeyResultLinkResponse> toLinkResponses(List<ProjectKeyResultLink> links) {
        Map<Long, BigDecimal> totalWeightsByKeyResult = totalWeightsByKeyResult(links);
        return links.stream()
                .map(link -> toLinkResponse(link, totalWeightsByKeyResult.getOrDefault(link.getKeyResult().getId(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))))
                .toList();
    }

    private ProjectDtos.ProjectKeyResultLinkResponse toLinkResponse(ProjectKeyResultLink link, BigDecimal totalWeight) {
        return new ProjectDtos.ProjectKeyResultLinkResponse(
                link.getId(),
                link.getProject() == null ? null : link.getProject().getId(),
                link.getProject() == null ? null : link.getProject().getName(),
                link.getKeyResult().getId(),
                link.getKeyResult().getDescription(),
                link.getContributionWeight(),
                link.getContributionType(),
                totalWeight,
                totalWeight.compareTo(BigDecimal.valueOf(100)) > 0,
                link.isActive(),
                link.getCreatedAt()
        );
    }

    ProjectDtos.ProjectResponse toProjectResponse(Project project) {
        ProjectDtos.ProjectResponse base = Mapper.toResponse(project);
        List<ProjectDtos.ProjectLinkedKeyResultResponse> linkedKeyResults = project.getId() == null
                ? List.of()
                : linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(project.getId())
                .stream()
                .map(this::toLinkedKeyResultResponse)
                .toList();
        return toProjectResponse(base, linkedKeyResults);
    }

    List<ProjectDtos.ProjectResponse> toProjectResponses(List<Project> projects) {
        List<Long> projectIds = projects.stream()
                .map(Project::getId)
                .filter(id -> id != null)
                .toList();
        Map<Long, List<ProjectDtos.ProjectLinkedKeyResultResponse>> linksByProjectId = projectIds.isEmpty()
                ? Map.of()
                : linkRepository.findActiveByProjectIds(projectIds)
                .stream()
                .collect(Collectors.groupingBy(
                        link -> link.getProject().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(this::toLinkedKeyResultResponse, Collectors.toList())
                ));
        return projects.stream()
                .map(project -> toProjectResponse(
                        Mapper.toResponse(project),
                        project.getId() == null ? List.of() : linksByProjectId.getOrDefault(project.getId(), List.of())
                ))
                .toList();
    }

    private ProjectDtos.ProjectLinkedKeyResultResponse toLinkedKeyResultResponse(ProjectKeyResultLink link) {
        return new ProjectDtos.ProjectLinkedKeyResultResponse(
                link.getId(),
                link.getKeyResult().getId(),
                link.getKeyResult().getName(),
                link.getKeyResult().getDescription(),
                link.getContributionWeight(),
                link.getContributionType(),
                link.isActive()
        );
    }

    private ProjectDtos.ProjectResponse toProjectResponse(
            ProjectDtos.ProjectResponse base,
            List<ProjectDtos.ProjectLinkedKeyResultResponse> linkedKeyResults
    ) {
        return new ProjectDtos.ProjectResponse(
                base.id(),
                base.externalProjectId(),
                base.externalSource(),
                base.name(),
                base.description(),
                base.type(),
                base.departmentId(),
                base.departmentName(),
                base.status(),
                base.startPeriod(),
                base.endPeriod(),
                base.startDate(),
                base.endDate(),
                base.actualEndDate(),
                base.globalProgress(),
                base.keyResultId(),
                base.keyResultName(),
                base.contributionWeight(),
                base.linkStatus(),
                base.jiraKey(),
                base.tutors(),
                linkedKeyResults,
                base.origin(),
                base.syncStatus(),
                base.lastSyncedAt(),
                base.createdAt(),
                base.updatedAt()
        );
    }

    private ProjectDtos.ImpactChainItemResponse toImpactItem(ProjectKeyResultLink link) {
        KeyResult keyResult = link.getKeyResult();
        boolean completed = link.getProject() != null && link.getProject().getStatus() == ProjectStatus.FINALIZADO;
        BigDecimal appliedContribution = completed ? link.getContributionWeight() : BigDecimal.ZERO;
        return new ProjectDtos.ImpactChainItemResponse(
                link.getId(),
                keyResult.getId(),
                keyResult.getDescription(),
                keyResult.getObjective().getId(),
                keyResult.getObjective().getName(),
                link.getContributionWeight(),
                link.getContributionType(),
                appliedContribution.setScale(2, RoundingMode.HALF_UP),
                completed,
                periodOf(link.getProject())
        );
    }

    private BigDecimal totalWeightForKeyResult(Long keyResultId) {
        return totalWeightsByKeyResult(List.of(linkWithKeyResultId(keyResultId)))
                .getOrDefault(keyResultId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private Map<Long, BigDecimal> totalWeightsByKeyResult(List<ProjectKeyResultLink> links) {
        List<Long> keyResultIds = links.stream()
                .map(ProjectKeyResultLink::getKeyResult)
                .filter(keyResult -> keyResult != null && keyResult.getId() != null)
                .map(KeyResult::getId)
                .distinct()
                .toList();
        if (keyResultIds.isEmpty()) {
            return Map.of();
        }
        return linkRepository.sumActiveContributionWeightsByKeyResultIds(keyResultIds)
                .stream()
                .collect(Collectors.toMap(
                        ProjectKeyResultLinkRepository.KeyResultWeightTotal::getKeyResultId,
                        total -> total.getTotalWeight().setScale(2, RoundingMode.HALF_UP),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    private ProjectKeyResultLink linkWithKeyResultId(Long keyResultId) {
        KeyResult keyResult = new KeyResult();
        keyResult.setId(keyResultId);
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResult);
        return link;
    }

    private String periodOf(Project project) {
        if (project == null) {
            return null;
        }
        return project.getEndPeriod() == null || project.getEndPeriod().isBlank()
                ? project.getStartPeriod()
                : project.getEndPeriod();
    }
}
