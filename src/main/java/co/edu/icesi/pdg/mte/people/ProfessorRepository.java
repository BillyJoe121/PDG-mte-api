package co.edu.icesi.pdg.mte.people;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {
    @Override
    @EntityGraph(attributePaths = "department")
    List<Professor> findAll();

    @Override
    @EntityGraph(attributePaths = "department")
    Page<Professor> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "department")
    List<Professor> findByDepartmentId(Long departmentId);

    @EntityGraph(attributePaths = "department")
    Page<Professor> findByDepartmentId(Long departmentId, Pageable pageable);

    Optional<Professor> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByDepartmentId(Long departmentId);
}
