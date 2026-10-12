package co.edu.icesi.pdg.mte.operations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DependencyExceptionRepository extends JpaRepository<DependencyException, Long> {
    Optional<DependencyException> findByFindingId(String findingId);
    Optional<DependencyException> findByCveId(String cveId);
}
