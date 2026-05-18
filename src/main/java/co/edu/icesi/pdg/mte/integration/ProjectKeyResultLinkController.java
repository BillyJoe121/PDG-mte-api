package co.edu.icesi.pdg.mte.integration;

import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/project-key-result-links")
public class ProjectKeyResultLinkController {

    private final ProjectKeyResultLinkService service;

    public ProjectKeyResultLinkController(ProjectKeyResultLinkService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA','JEFE_DPTO')")
    public ProjectDtos.ProjectKeyResultLinkResponse link(@Valid @RequestBody ProjectDtos.ProjectKeyResultLinkRequest request) {
        return service.link(request);
    }

    @GetMapping
    public List<ProjectDtos.ProjectKeyResultLinkResponse> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long keyResultId
    ) {
        return service.list(projectId, keyResultId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA','JEFE_DPTO')")
    public void unlink(@PathVariable Long id) {
        service.unlink(id);
    }
}
