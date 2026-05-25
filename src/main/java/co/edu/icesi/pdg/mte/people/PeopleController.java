package co.edu.icesi.pdg.mte.people;

import co.edu.icesi.pdg.mte.api.dto.PeopleDtos;
import co.edu.icesi.pdg.mte.common.Pagination;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PeopleController {
    private final PeopleService peopleService;

    public PeopleController(PeopleService peopleService) {
        this.peopleService = peopleService;
    }

    @GetMapping("/roles")
    List<PeopleDtos.RoleResponse> listRoles() {
        return peopleService.listRoles();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    PeopleDtos.RoleResponse createRole(@Valid @RequestBody PeopleDtos.RoleRequest request) {
        return peopleService.createRole(request);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    PeopleDtos.RoleResponse updateRole(@PathVariable Long id, @Valid @RequestBody PeopleDtos.RoleRequest request) {
        return peopleService.updateRole(id, request);
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void deleteRole(@PathVariable Long id) {
        peopleService.deleteRole(id);
    }

    @GetMapping("/positions")
    List<PeopleDtos.PositionResponse> listPositions() {
        return peopleService.listPositions();
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    PeopleDtos.PositionResponse createPosition(@Valid @RequestBody PeopleDtos.PositionRequest request) {
        return peopleService.createPosition(request);
    }

    @PutMapping("/positions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    PeopleDtos.PositionResponse updatePosition(@PathVariable Long id, @Valid @RequestBody PeopleDtos.PositionRequest request) {
        return peopleService.updatePosition(id, request);
    }

    @DeleteMapping("/positions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void deletePosition(@PathVariable Long id) {
        peopleService.deletePosition(id);
    }

    @GetMapping("/professors")
    Object listProfessors(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (Pagination.requested(page, size)) {
            return peopleService.listProfessorsPage(departmentId, page, size);
        }
        return peopleService.listProfessors(departmentId);
    }

    @PostMapping("/professors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    PeopleDtos.ProfessorResponse createProfessor(@Valid @RequestBody PeopleDtos.ProfessorRequest request) {
        return peopleService.createProfessor(request);
    }

    @PutMapping("/professors/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    PeopleDtos.ProfessorResponse updateProfessor(@PathVariable Long id, @Valid @RequestBody PeopleDtos.ProfessorRequest request) {
        return peopleService.updateProfessor(id, request);
    }

    @DeleteMapping("/professors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void deleteProfessor(@PathVariable Long id) {
        peopleService.deleteProfessor(id);
    }

    @GetMapping("/professors/{id}/positions")
    List<PeopleDtos.TeacherPositionResponse> listTeacherPositions(@PathVariable Long id) {
        return peopleService.listTeacherPositions(id);
    }

    @PostMapping("/professors/{id}/positions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    PeopleDtos.TeacherPositionResponse assignPosition(
            @PathVariable Long id,
            @Valid @RequestBody PeopleDtos.TeacherPositionRequest request
    ) {
        return peopleService.assignPosition(id, request);
    }

    @DeleteMapping("/professors/{id}/positions/{positionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void removePosition(@PathVariable Long id, @PathVariable Long positionId) {
        peopleService.removePosition(id, positionId);
    }
}
