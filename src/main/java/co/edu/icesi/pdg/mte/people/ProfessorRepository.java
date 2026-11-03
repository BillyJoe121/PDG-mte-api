package co.edu.icesi.pdg.mte.people;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {
    Optional<Professor> findByEmailIgnoreCase(String email);

    Optional<Professor> findByAuthUserId(UUID authUserId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByDepartmentId(Long departmentId);

    /**
     * Active people with the given access role, locked for update in id order until the
     * surrounding transaction ends. A concurrent caller waits and then sees the committed state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Professor> findByAccessRoleAndActiveTrueOrderByIdAsc(String accessRole);
}
