package co.edu.icesi.pdg.mte.project;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectTeacherRepository extends JpaRepository<ProjectTeacher, ProjectTeacherId> {
    @EntityGraph(attributePaths = {"project", "teacher", "role"})
    List<ProjectTeacher> findByProject_Id(Long projectId);

    boolean existsByProject_Id(Long projectId);

    boolean existsByTeacher_Id(Long teacherId);

    boolean existsByRole_Id(Long roleId);
}
