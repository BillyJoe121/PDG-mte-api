package co.edu.icesi.pdg.mte.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {
    Optional<Professor> findByEmailIgnoreCase(String email);

    Optional<Professor> findByAuthUserId(UUID authUserId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByDepartmentId(Long departmentId);

    long countByAccessRoleAndActiveTrue(String accessRole);
}
