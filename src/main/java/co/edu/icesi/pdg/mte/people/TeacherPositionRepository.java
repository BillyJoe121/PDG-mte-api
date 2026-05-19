package co.edu.icesi.pdg.mte.people;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherPositionRepository extends JpaRepository<TeacherPosition, TeacherPositionId> {
    boolean existsByTeacher_Id(Long teacherId);

    boolean existsByPosition_Id(Long positionId);
}
