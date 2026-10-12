package co.edu.icesi.pdg.mte.measurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CycleClosureRepository extends JpaRepository<CycleClosure, Long> {
    Optional<CycleClosure> findByCycleId(Long cycleId);
}
