package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        return new ProjectDtos.ProjectKpiResponse(
                history.size(),
                links.size(),
                project.getStatus() == ProjectStatus.FINALIZADO
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
        return new ProjectDtos.ProjectKeyResultLinkResponse(
                link.getId(),
                link.getProject() == null ? null : link.getProject().getId(),
                link.getProject() == null ? null : link.getProject().getName(),
                link.getKeyResult().getId(),
                link.getKeyResult().getDescription(),
                link.isActive(),
                link.getCreatedAt()
        );
    }

    ProjectDtos.ProjectResponse toProjectResponse(Project project) {
        return toProjectResponse(project, project.getId() == null
                ? List.of()
                : linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(project.getId()));
    }

    /** Builds a list with one query for the active links of every project, instead of one per project. */
    List<ProjectDtos.ProjectResponse> toProjectResponses(List<Project> projects) {
        List<Long> ids = projects.stream().map(Project::getId).filter(Objects::nonNull).toList();
        Map<Long, List<ProjectKeyResultLink>> linksByProject = ids.isEmpty()
                ? Map.of()
                : linkRepository.findByProjectIdInAndActiveTrueOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(link -> link.getProject().getId()));
        return projects.stream()
                .map(project -> toProjectResponse(project, project.getId() == null
                        ? List.of()
                        : linksByProject.getOrDefault(project.getId(), List.of())))
                .toList();
    }

    private ProjectDtos.ProjectResponse toProjectResponse(Project project, List<ProjectKeyResultLink> activeLinks) {
        ProjectDtos.ProjectResponse base = Mapper.toResponse(project);
        List<ProjectDtos.ProjectLinkedKeyResultResponse> linkedKeyResults = activeLinks
                .stream()
                .map(link -> new ProjectDtos.ProjectLinkedKeyResultResponse(
                        link.getId(),
                        link.getKeyResult().getId(),
                        link.getKeyResult().getName(),
                        link.getKeyResult().getDescription(),
                        link.isActive()
                ))
                .toList();
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
                base.linkStatus(),
                base.jiraKey(),
                base.plannerUrl(),
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
        return new ProjectDtos.ImpactChainItemResponse(
                link.getId(),
                keyResult.getId(),
                keyResult.getDescription(),
                Mapper.goalNames(keyResult),
                Mapper.strategicBetNames(keyResult),
                completed,
                periodOf(link.getProject())
        );
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
