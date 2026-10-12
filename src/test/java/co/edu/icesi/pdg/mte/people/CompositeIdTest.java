package co.edu.icesi.pdg.mte.people;

import co.edu.icesi.pdg.mte.project.ProjectTeacherId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeIdTest {

    @Test
    void teacherPositionIdImplementsACompleteValueContract() {
        TeacherPositionId value = new TeacherPositionId(1L, 2L);
        TeacherPositionId equal = new TeacherPositionId();
        equal.setPositionId(1L); equal.setTeacherId(2L);

        assertThat(value).isEqualTo(value).isEqualTo(equal).hasSameHashCodeAs(equal);
        assertThat(value).isNotEqualTo(null).isNotEqualTo("other")
                .isNotEqualTo(new TeacherPositionId(9L, 2L))
                .isNotEqualTo(new TeacherPositionId(1L, 9L));
        assertThat(value.getPositionId()).isEqualTo(1L);
        assertThat(value.getTeacherId()).isEqualTo(2L);
    }

    @Test
    void projectTeacherIdImplementsACompleteValueContract() {
        ProjectTeacherId value = new ProjectTeacherId(1L, 2L, 3L);
        ProjectTeacherId equal = new ProjectTeacherId();
        equal.setProjectId(1L); equal.setTeacherId(2L); equal.setRoleId(3L);

        assertThat(value).isEqualTo(value).isEqualTo(equal).hasSameHashCodeAs(equal);
        assertThat(value).isNotEqualTo(null).isNotEqualTo("other")
                .isNotEqualTo(new ProjectTeacherId(9L, 2L, 3L))
                .isNotEqualTo(new ProjectTeacherId(1L, 9L, 3L))
                .isNotEqualTo(new ProjectTeacherId(1L, 2L, 9L));
        assertThat(value.getProjectId()).isEqualTo(1L);
        assertThat(value.getTeacherId()).isEqualTo(2L);
        assertThat(value.getRoleId()).isEqualTo(3L);
    }
}
