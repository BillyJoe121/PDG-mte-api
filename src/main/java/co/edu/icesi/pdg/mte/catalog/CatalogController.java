package co.edu.icesi.pdg.mte.catalog;

import co.edu.icesi.pdg.mte.api.dto.CatalogDtos;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/catalogs/bootstrap")
    ResponseEntity<CatalogDtos.CatalogBootstrapResponse> bootstrap() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .body(catalogService.bootstrap());
    }

    @GetMapping("/measurement-units")
    List<CatalogDtos.MeasurementUnitResponse> listUnits() {
        return catalogService.listUnits();
    }

    @PostMapping("/measurement-units")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    CatalogDtos.MeasurementUnitResponse createUnit(@Valid @RequestBody CatalogDtos.MeasurementUnitRequest request) {
        return catalogService.createUnit(request);
    }

    @PutMapping("/measurement-units/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    CatalogDtos.MeasurementUnitResponse updateUnit(
            @PathVariable Long id,
            @Valid @RequestBody CatalogDtos.MeasurementUnitRequest request
    ) {
        return catalogService.updateUnit(id, request);
    }

    @PatchMapping("/measurement-units/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    CatalogDtos.MeasurementUnitResponse updateUnitActive(
            @PathVariable Long id,
            @Valid @RequestBody CatalogDtos.MeasurementUnitActiveRequest request
    ) {
        return catalogService.updateUnitActive(id, request);
    }

    @DeleteMapping("/measurement-units/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void deleteUnit(@PathVariable Long id) {
        catalogService.deleteUnit(id);
    }

    @GetMapping("/academic-periods")
    List<CatalogDtos.AcademicPeriodResponse> listPeriods() {
        return catalogService.listPeriods();
    }

    @PostMapping("/academic-periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    CatalogDtos.AcademicPeriodResponse createPeriod(@Valid @RequestBody CatalogDtos.AcademicPeriodRequest request) {
        return catalogService.createPeriod(request);
    }

    @PutMapping("/academic-periods/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    CatalogDtos.AcademicPeriodResponse updatePeriod(
            @PathVariable Long id,
            @Valid @RequestBody CatalogDtos.AcademicPeriodRequest request
    ) {
        return catalogService.updatePeriod(id, request);
    }

    @PatchMapping("/academic-periods/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    CatalogDtos.AcademicPeriodResponse updatePeriodStatus(
            @PathVariable Long id,
            @Valid @RequestBody CatalogDtos.AcademicPeriodStatusRequest request
    ) {
        return catalogService.updatePeriodStatus(id, request);
    }

    @PatchMapping("/academic-periods/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    CatalogDtos.AcademicPeriodResponse updatePeriodActive(
            @PathVariable Long id,
            @Valid @RequestBody CatalogDtos.AcademicPeriodActiveRequest request
    ) {
        return catalogService.updatePeriodActive(id, request);
    }

    @DeleteMapping("/academic-periods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void deletePeriod(@PathVariable Long id) {
        catalogService.deletePeriod(id);
    }

    @GetMapping("/departments")
    List<CatalogDtos.DepartmentResponse> listDepartments() {
        return catalogService.listDepartments();
    }

    @GetMapping("/schools")
    List<CatalogDtos.SchoolResponse> listSchools() {
        return catalogService.listSchools();
    }

    @PostMapping("/schools")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    CatalogDtos.SchoolResponse createSchool(@Valid @RequestBody CatalogDtos.SchoolRequest request) {
        return catalogService.createSchool(request);
    }

    @PutMapping("/schools/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    CatalogDtos.SchoolResponse updateSchool(@PathVariable Long id, @Valid @RequestBody CatalogDtos.SchoolRequest request) {
        return catalogService.updateSchool(id, request);
    }

    @DeleteMapping("/schools/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void deleteSchool(@PathVariable Long id) {
        catalogService.deleteSchool(id);
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    CatalogDtos.DepartmentResponse createDepartment(@Valid @RequestBody CatalogDtos.DepartmentRequest request) {
        return catalogService.createDepartment(request);
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    CatalogDtos.DepartmentResponse updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody CatalogDtos.DepartmentRequest request
    ) {
        return catalogService.updateDepartment(id, request);
    }

    @DeleteMapping("/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void deleteDepartment(@PathVariable Long id) {
        catalogService.deleteDepartment(id);
    }
}
