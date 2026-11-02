package co.edu.icesi.pdg.mte.people;

import co.edu.icesi.pdg.mte.api.dto.PeopleDtos;
import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import co.edu.icesi.pdg.mte.security.AccountAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PeopleController {
    private final PeopleService peopleService;
    private final AccountAccessService accountAccessService;

    public PeopleController(PeopleService peopleService, AccountAccessService accountAccessService) {
        this.peopleService = peopleService;
        this.accountAccessService = accountAccessService;
    }

    @GetMapping("/directory-users")
    @PreAuthorize("hasRole('ADMIN')")
    List<PeopleDtos.DirectoryUserResponse> listDirectoryUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String accessRole,
            @RequestParam(required = false) Boolean active
    ) {
        return peopleService.listDirectoryUsers(search, departmentId, accessRole, active);
    }

    @PostMapping("/directory-users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    PeopleDtos.DirectoryUserResponse createDirectoryUser(@Valid @RequestBody PeopleDtos.DirectoryUserRequest request) {
        return peopleService.createDirectoryUser(request);
    }

    @PutMapping("/directory-users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    PeopleDtos.DirectoryUserResponse updateDirectoryUser(
            @PathVariable Long id,
            @Valid @RequestBody PeopleDtos.DirectoryUserRequest request
    ) {
        return peopleService.updateDirectoryUser(id, request);
    }

    @PatchMapping("/directory-users/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    PeopleDtos.DirectoryUserResponse updateDirectoryUserActive(
            @PathVariable Long id,
            @Valid @RequestBody PeopleDtos.DirectoryUserActiveRequest request
    ) {
        return peopleService.updateDirectoryUserActive(id, request.active());
    }

    @PostMapping("/directory-users/{id}/access-link")
    @PreAuthorize("hasRole('ADMIN')")
    SecurityDtos.AccessLinkResponse createAccessLink(@PathVariable Long id) {
        return accountAccessService.issueAccessLink(id);
    }

    @DeleteMapping("/directory-users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void deleteDirectoryUser(@PathVariable Long id) {
        peopleService.deleteDirectoryUser(id);
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
    List<PeopleDtos.ProfessorResponse> listProfessors(@RequestParam(required = false) Long departmentId) {
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
