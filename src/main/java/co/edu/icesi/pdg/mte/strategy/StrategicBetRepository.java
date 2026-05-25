package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StrategicBetRepository extends JpaRepository<StrategicBet, Long> {
    @Override
    @EntityGraph(attributePaths = "world")
    List<StrategicBet> findAll();

    boolean existsByNameIgnoreCase(String name);

    Optional<StrategicBet> findByNameIgnoreCase(String name);

    boolean existsByWorldId(Long worldId);
}
