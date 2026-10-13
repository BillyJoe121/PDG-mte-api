package co.edu.icesi.pdg.mte.catalog;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.CatalogDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceOrganizationTest extends CatalogServiceTestSupport {

    @Test
    void listsCatalogs() {
        MeasurementUnit zetaUnit = TestFixtures.unit(1L);
        zetaUnit.setName("Zeta");
        MeasurementUnit alphaUnit = TestFixtures.unit(2L);
        alphaUnit.setName("Alpha");
        AcademicPeriod laterPeriod = TestFixtures.period(1L);
        laterPeriod.setName("2026-2");
        laterPeriod.setStartDate(LocalDate.of(2026, 7, 1));
        AcademicPeriod earlierPeriod = TestFixtures.period(2L);
        earlierPeriod.setName("2026-1");
        earlierPeriod.setStartDate(LocalDate.of(2026, 1, 1));

        when(unitRepository.findAll()).thenReturn(List.of(zetaUnit, alphaUnit));
        when(periodRepository.findAll()).thenReturn(List.of(laterPeriod, earlierPeriod));
        when(departmentRepository.findAll()).thenReturn(List.of(TestFixtures.department(1L)));

        assertThat(service.listUnits()).extracting(CatalogDtos.MeasurementUnitResponse::name)
                .containsExactly("Alpha", "Zeta");
        assertThat(service.listPeriods()).extracting(CatalogDtos.AcademicPeriodResponse::name)
                .containsExactly("2026-1", "2026-2");
        assertThat(service.listDepartments()).hasSize(1);
    }

    @Test
    void managesSchoolsAndRejectsDuplicatesOrAssignedDepartments() {
        School school = new School(); school.setId(1L); school.setName("Ingeniería"); school.setDescription("Desc");
        School other = new School(); other.setId(2L); other.setName("Otra");
        when(schoolRepository.save(any(School.class))).thenAnswer(invocation -> {
            School saved = invocation.getArgument(0); if (saved.getId() == null) saved.setId(1L); return saved;
        });
        assertThat(service.createSchool(new CatalogDtos.SchoolRequest(" Ingeniería ", "Desc")).name()).isEqualTo("Ingeniería");
        when(schoolRepository.existsByNameIgnoreCase("Ingeniería")).thenReturn(true);
        assertThatThrownBy(() -> service.createSchool(new CatalogDtos.SchoolRequest("Ingeniería", null)))
                .isInstanceOf(BusinessException.class);

        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(schoolRepository.findByNameIgnoreCase("Otra")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.updateSchool(1L, new CatalogDtos.SchoolRequest("Otra", null)))
                .isInstanceOf(BusinessException.class);
        when(schoolRepository.findByNameIgnoreCase("Ingeniería")).thenReturn(Optional.of(school));
        assertThat(service.updateSchool(1L, new CatalogDtos.SchoolRequest("Ingeniería", "Nueva")).description()).isEqualTo("Nueva");

        when(departmentRepository.existsBySchoolId(1L)).thenReturn(true, false);
        assertThatThrownBy(() -> service.deleteSchool(1L)).isInstanceOf(BusinessException.class).hasMessageContaining("departamentos");
        service.deleteSchool(1L);
        verify(schoolRepository).delete(school);
    }

    @Test
    void listsSchoolsAndReportsMissingSchool() {
        School zeta = new School(); zeta.setId(2L); zeta.setName("Zeta");
        School alpha = new School(); alpha.setId(1L); alpha.setName("alpha");
        when(schoolRepository.findAll()).thenReturn(List.of(zeta, alpha));
        assertThat(service.listSchools()).extracting(CatalogDtos.SchoolResponse::name).containsExactly("alpha", "Zeta");
        when(schoolRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteSchool(99L)).isInstanceOf(BusinessException.class).hasMessageContaining("no encontrada");
    }

    @Test
    void managesDepartmentsAndProtectsEveryUsageType() {
        School school = new School(); school.setId(1L); school.setName("Ingeniería");
        Department department = TestFixtures.department(1L); department.setSchool(school);
        Department other = TestFixtures.department(2L); other.setName("Otro"); other.setSchool(school);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> {
            Department saved = invocation.getArgument(0); if (saved.getId() == null) saved.setId(1L); return saved;
        });
        var request = new CatalogDtos.DepartmentRequest(" DCSI ", "Desc", 1L, 44L);
        assertThat(service.createDepartment(request)).extracting(CatalogDtos.DepartmentResponse::name, CatalogDtos.DepartmentResponse::externalDepartmentId)
                .containsExactly("DCSI", 44L);
        when(departmentRepository.existsByNameIgnoreCase("DCSI")).thenReturn(true);
        assertThatThrownBy(() -> service.createDepartment(request)).isInstanceOf(BusinessException.class);

        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(departmentRepository.findByNameIgnoreCase("Otro")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.updateDepartment(1L, new CatalogDtos.DepartmentRequest("Otro", null, 1L, null)))
                .isInstanceOf(BusinessException.class);
        when(departmentRepository.findByNameIgnoreCase("DCSI")).thenReturn(Optional.of(department));
        assertThat(service.updateDepartment(1L, request).externalDepartmentId()).isEqualTo(44L);

        when(projectRepository.existsByDepartmentId(1L)).thenReturn(true, false);
        assertThatThrownBy(() -> service.deleteDepartment(1L)).isInstanceOf(BusinessException.class);
        service.deleteDepartment(1L);
        verify(departmentRepository).delete(department);
    }
}
