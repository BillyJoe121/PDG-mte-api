package co.edu.icesi.pdg.mte.catalog;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.CatalogDtos;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoalRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import co.edu.icesi.pdg.mte.strategy.ObjectiveRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private MeasurementUnitRepository unitRepository;

    @Mock
    private AcademicPeriodRepository periodRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private InstitutionalGoalRepository goalRepository;

    @Mock
    private KeyResultRepository keyResultRepository;

    @Mock
    private ObjectiveRepository objectiveRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private CatalogService service;

    @Test
    void createsMeasurementUnitWhenNameIsUnique() {
        when(unitRepository.existsByNameIgnoreCase("Horas")).thenReturn(false);
        when(unitRepository.save(any(MeasurementUnit.class))).thenAnswer(invocation -> {
            MeasurementUnit unit = invocation.getArgument(0);
            unit.setId(1L);
            return unit;
        });

        var response = service.createUnit(new CatalogDtos.MeasurementUnitRequest(
                "Horas",
                MeasurementUnitType.NUMERICA,
                "Tiempo invertido"
        ));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Horas");
        verify(unitRepository).save(any(MeasurementUnit.class));
    }

    @Test
    void rejectsDuplicatedMeasurementUnitName() {
        when(unitRepository.existsByNameIgnoreCase("Horas")).thenReturn(true);

        assertThatThrownBy(() -> service.createUnit(new CatalogDtos.MeasurementUnitRequest(
                "Horas",
                MeasurementUnitType.NUMERICA,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe");

        verify(unitRepository, never()).save(any());
    }

    @Test
    void updatesMeasurementUnitWhenNameIsAvailable() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.findByNameIgnoreCase("Horas actualizadas")).thenReturn(Optional.empty());
        when(unitRepository.save(unit)).thenReturn(unit);

        var response = service.updateUnit(1L, new CatalogDtos.MeasurementUnitRequest(
                "Horas actualizadas",
                MeasurementUnitType.NUMERICA,
                "Nueva descripcion"
        ));

        assertThat(response.name()).isEqualTo("Horas actualizadas");
        assertThat(response.type()).isEqualTo(MeasurementUnitType.NUMERICA);
        assertThat(response.description()).isEqualTo("Nueva descripcion");
    }

    @Test
    void updatesMeasurementUnitWhenNameBelongsToSameRecord() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.findByNameIgnoreCase("Porcentaje")).thenReturn(Optional.of(unit));
        when(unitRepository.save(unit)).thenReturn(unit);

        var response = service.updateUnit(1L, new CatalogDtos.MeasurementUnitRequest(
                "Porcentaje",
                MeasurementUnitType.PORCENTAJE,
                "Mismo registro"
        ));

        assertThat(response.name()).isEqualTo("Porcentaje");
    }

    @Test
    void rejectsChangingTypeOfMeasurementUnitUsedByGoals() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.findByNameIgnoreCase("Porcentaje")).thenReturn(Optional.of(unit));
        when(goalRepository.existsByMeasurementUnitId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateUnit(1L, new CatalogDtos.MeasurementUnitRequest(
                "Porcentaje",
                MeasurementUnitType.BOOLEANA,
                "Nueva regla"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("tipo")
                .hasMessageContaining("en uso");

        verify(unitRepository, never()).save(any());
    }

    @Test
    void normalizesOptionalMeasurementUnitDescription() {
        when(unitRepository.existsByNameIgnoreCase("Horas")).thenReturn(false);
        when(unitRepository.save(any(MeasurementUnit.class))).thenAnswer(invocation -> {
            MeasurementUnit saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        var response = service.createUnit(new CatalogDtos.MeasurementUnitRequest(
                "  Horas  ",
                MeasurementUnitType.NUMERICA,
                "   "
        ));

        assertThat(response.name()).isEqualTo("Horas");
        assertThat(response.description()).isNull();
    }

    @Test
    void togglesMeasurementUnitActiveFlag() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.save(unit)).thenReturn(unit);

        var response = service.updateUnitActive(1L, new CatalogDtos.MeasurementUnitActiveRequest(false));

        assertThat(response.active()).isFalse();
    }

    @Test
    void deletesMeasurementUnitWhenItIsNotInUse() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));

        service.deleteUnit(1L);

        verify(unitRepository).delete(unit);
        verify(auditService).record(eq(AuditAction.DELETE), eq("MEASUREMENT_UNIT"), eq(1L), anyString(), any(), isNull());
    }

    @Test
    void rejectsUpdatingMissingMeasurementUnit() {
        when(unitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUnit(99L, new CatalogDtos.MeasurementUnitRequest(
                "Horas",
                MeasurementUnitType.NUMERICA,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("no encontrada");
    }

    @Test
    void rejectsUpdatingMeasurementUnitWithDuplicatedName() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        MeasurementUnit duplicated = TestFixtures.unit(2L);
        duplicated.setName("Horas");
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.findByNameIgnoreCase("Horas")).thenReturn(Optional.of(duplicated));

        assertThatThrownBy(() -> service.updateUnit(1L, new CatalogDtos.MeasurementUnitRequest(
                "Horas",
                MeasurementUnitType.NUMERICA,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void rejectsDeletingMissingMeasurementUnit() {
        when(unitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUnit(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no encontrada");
    }

    @Test
    void rejectsDeletingMeasurementUnitUsedByGoal() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(goalRepository.existsByMeasurementUnitId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteUnit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en uso");

        verify(unitRepository, never()).delete(any());
    }

    @Test
    void rejectsDeletingMeasurementUnitUsedByKeyResult() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(keyResultRepository.existsByMeasurementUnitId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteUnit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en uso");
    }

    @Test
    void createsAcademicPeriodWhenDatesAndNameAreValid() {
        when(periodRepository.existsByNameIgnoreCase("2026-1")).thenReturn(false);
        when(periodRepository.save(any(AcademicPeriod.class))).thenAnswer(invocation -> {
            AcademicPeriod period = invocation.getArgument(0);
            period.setId(1L);
            return period;
        });

        var response = service.createPeriod(new CatalogDtos.AcademicPeriodRequest(
                "2026-1",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                PeriodStatus.ACTIVO
        ));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(PeriodStatus.ACTIVO);
    }

    @Test
    void rejectsAcademicPeriodWhenEndDateIsNotAfterStartDate() {
        assertThatThrownBy(() -> service.createPeriod(new CatalogDtos.AcademicPeriodRequest(
                "2026-1",
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 1, 1),
                PeriodStatus.ACTIVO
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("fecha de fin");

        verify(periodRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicatedAcademicPeriodName() {
        when(periodRepository.existsByNameIgnoreCase("2026-1")).thenReturn(true);

        assertThatThrownBy(() -> service.createPeriod(new CatalogDtos.AcademicPeriodRequest(
                "2026-1",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                PeriodStatus.ACTIVO
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void updatesAcademicPeriodWhenNameAndDatesAreValid() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(periodRepository.findByNameIgnoreCase("2026-2")).thenReturn(Optional.empty());
        when(periodRepository.save(period)).thenReturn(period);

        var response = service.updatePeriod(1L, new CatalogDtos.AcademicPeriodRequest(
                "2026-2",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 12, 15),
                PeriodStatus.PLANIFICACION
        ));

        assertThat(response.name()).isEqualTo("2026-2");
        assertThat(response.status()).isEqualTo(PeriodStatus.PLANIFICACION);
    }

    @Test
    void updatesAcademicPeriodWhenNameBelongsToSameRecord() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(periodRepository.findByNameIgnoreCase("2026-1")).thenReturn(Optional.of(period));
        when(periodRepository.save(period)).thenReturn(period);

        var response = service.updatePeriod(1L, new CatalogDtos.AcademicPeriodRequest(
                "2026-1",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                PeriodStatus.ACTIVO
        ));

        assertThat(response.name()).isEqualTo("2026-1");
    }

    @Test
    void updatesAcademicPeriodStatus() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(periodRepository.save(period)).thenReturn(period);

        var response = service.updatePeriodStatus(1L, new CatalogDtos.AcademicPeriodStatusRequest(PeriodStatus.CERRADO));

        assertThat(response.status()).isEqualTo(PeriodStatus.CERRADO);
    }

    @Test
    void togglesAcademicPeriodActiveState() {
        AcademicPeriod period = TestFixtures.period(1L);
        period.setStatus(PeriodStatus.PLANIFICACION);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(periodRepository.save(period)).thenReturn(period);

        var activeResponse = service.updatePeriodActive(1L, new CatalogDtos.AcademicPeriodActiveRequest(true));
        var inactiveResponse = service.updatePeriodActive(1L, new CatalogDtos.AcademicPeriodActiveRequest(false));

        assertThat(activeResponse.status()).isEqualTo(PeriodStatus.ACTIVO);
        assertThat(inactiveResponse.status()).isEqualTo(PeriodStatus.CERRADO);
    }

    @Test
    void deletesAcademicPeriodWhenItIsNotInUse() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));

        service.deletePeriod(1L);

        verify(periodRepository).delete(period);
        verify(auditService).record(eq(AuditAction.DELETE), eq("ACADEMIC_PERIOD"), eq(1L), anyString(), any(), isNull());
    }

    @Test
    void rejectsUpdatingMissingAcademicPeriod() {
        when(periodRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePeriod(99L, new CatalogDtos.AcademicPeriodRequest(
                "2026-2",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 12, 15),
                PeriodStatus.PLANIFICACION
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("no encontrado");
    }

    @Test
    void rejectsUpdatingAcademicPeriodWithInvalidDates() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.updatePeriod(1L, new CatalogDtos.AcademicPeriodRequest(
                "2026-2",
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2026, 7, 1),
                PeriodStatus.PLANIFICACION
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("fecha de fin");
    }

    @Test
    void rejectsUpdatingAcademicPeriodWithDuplicatedName() {
        AcademicPeriod period = TestFixtures.period(1L);
        AcademicPeriod duplicated = TestFixtures.period(2L);
        duplicated.setName("2026-2");
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(periodRepository.findByNameIgnoreCase("2026-2")).thenReturn(Optional.of(duplicated));

        assertThatThrownBy(() -> service.updatePeriod(1L, new CatalogDtos.AcademicPeriodRequest(
                "2026-2",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 12, 15),
                PeriodStatus.PLANIFICACION
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void rejectsDeletingAcademicPeriodUsedByGoalPeriod() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(goalRepository.existsByPeriodsId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deletePeriod(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en uso");

        verify(periodRepository, never()).delete(any());
    }

    @Test
    void rejectsDeletingAcademicPeriodUsedByObjective() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(objectiveRepository.existsByAcademicPeriodId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deletePeriod(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en uso");
    }

    @Test
    void rejectsDeletingAcademicPeriodUsedByProject() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(projectRepository.existsByStartPeriodOrEndPeriod("2026-1", "2026-1")).thenReturn(true);

        assertThatThrownBy(() -> service.deletePeriod(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en uso");

        verify(periodRepository, never()).delete(any());
    }

    @Test
    void rejectsDeletingMissingAcademicPeriod() {
        when(periodRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePeriod(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no encontrado");
    }

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

        when(objectiveRepository.existsByDepartmentId(1L)).thenReturn(true, false, false);
        when(projectRepository.existsByDepartmentId(1L)).thenReturn(true, false);
        assertThatThrownBy(() -> service.deleteDepartment(1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.deleteDepartment(1L)).isInstanceOf(BusinessException.class);
        service.deleteDepartment(1L);
        verify(departmentRepository).delete(department);
    }
}
