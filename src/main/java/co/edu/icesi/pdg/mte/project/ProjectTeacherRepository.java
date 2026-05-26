package co.edu.icesi.pdg.mte.project;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTeacherRepository extends JpaRepository<ProjectTeacher, ProjectTeacherId> {
    boolean existsByProject_Id(Long projectId);

    boolean existsByTeacher_Id(Long teacherId);

    boolean existsByRole_Id(Long roleId);
}
