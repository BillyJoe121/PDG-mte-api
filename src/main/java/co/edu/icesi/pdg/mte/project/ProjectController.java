package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final ProjectKeyResultLinkService linkService;

    public ProjectController(ProjectService projectService, ProjectKeyResultLinkService linkService) {
        this.projectService = projectService;
        this.linkService = linkService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA','JEFE_DPTO','PROFESOR')")
    public ProjectDtos.ProjectResponse create(@Valid @RequestBody ProjectDtos.ProjectRequest request) {
        return projectService.create(request);
    }

    @GetMapping
    public List<ProjectDtos.ProjectResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) ProjectType type,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String period
    ) {
        return projectService.list(search, status, type, departmentId, period);
    }

    @GetMapping("/{id}")
    public ProjectDtos.ProjectResponse get(@PathVariable Long id) {
        return projectService.get(id);
    }

    @GetMapping("/{id}/detail")
    public ProjectDtos.ProjectDetailResponse detail(@PathVariable Long id) {
        return projectService.detail(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA','JEFE_DPTO')")
    public ProjectDtos.ProjectResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ProjectDtos.ProjectUpdateRequest request
    ) {
        return projectService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA')")
    public ProjectDtos.ProjectResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProjectDtos.ProjectStatusRequest request
    ) {
        return projectService.updateStatus(id, request);
    }

    @PostMapping("/{id}/progress")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA','JEFE_DPTO','PROFESOR')")
    public ProjectDtos.ProjectProgressResponse registerProgress(
            @PathVariable Long id,
            @Valid @RequestBody ProjectDtos.ProjectProgressRequest request
    ) {
        return projectService.registerProgress(id, request);
    }

    @GetMapping("/{id}/history")
    public List<ProjectDtos.ProjectProgressResponse> history(@PathVariable Long id) {
        return projectService.history(id);
    }

    @GetMapping("/{id}/impact-chain")
    public ProjectDtos.ImpactChainResponse impactChain(@PathVariable Long id) {
        return linkService.impactChain(id);
    }

    @GetMapping("/{id}/contribution-chain")
    public ProjectDtos.ImpactChainResponse contributionChain(@PathVariable Long id) {
        return linkService.impactChain(id);
    }

    @PostMapping("/sync/trayectoria")
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA')")
    public ProjectDtos.ProjectSyncResponse syncTrayectoria(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return projectService.syncFromTrayectoria(projectService.bearerValue(authorizationHeader));
    }
}
