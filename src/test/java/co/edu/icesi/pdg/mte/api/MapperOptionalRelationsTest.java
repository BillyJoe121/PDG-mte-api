package co.edu.icesi.pdg.mte.api;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapperOptionalRelationsTest {

    @Test
    void keyResultResponseOmitsThePeriodWhenNoneIsAssigned() {
        KeyResult withPeriod = TestFixtures.keyResult(1L, TestFixtures.unit(1L), TestFixtures.period(3L), null, null);
        KeyResult withoutPeriod = TestFixtures.keyResult(2L, TestFixtures.unit(1L));
        withoutPeriod.setAcademicPeriod(null);

        var assigned = Mapper.toResponse(withPeriod);
        var unassigned = Mapper.toResponse(withoutPeriod);

        assertThat(assigned.academicPeriodId()).isEqualTo(3L);
        assertThat(assigned.academicPeriodName()).isEqualTo("2026-1");
        assertThat(unassigned.academicPeriodId()).isNull();
        assertThat(unassigned.academicPeriodName()).isNull();
    }

    @Test
    void projectResponseFallsBackToTheStoredDepartmentNameWithoutADepartment() {
        Project synced = project(null);
        synced.setDepartmentName("Departamento externo");
        Department department = TestFixtures.department(5L);
        Project local = project(department);

        var external = Mapper.toResponse(synced);
        var internal = Mapper.toResponse(local);

        assertThat(external.departmentId()).isNull();
        assertThat(external.departmentName()).isEqualTo("Departamento externo");
        assertThat(internal.departmentId()).isEqualTo(5L);
        assertThat(internal.departmentName()).isEqualTo(department.getName());
    }

    private static Project project(Department department) {
        Project project = new Project();
        project.setId(1L);
        project.setName("Proyecto");
        project.setDescription("Descripcion");
        project.setType(ProjectType.INVESTIGACION);
        project.setStatus(ProjectStatus.ACTIVO);
        project.setDepartment(department);
        return project;
    }
}
