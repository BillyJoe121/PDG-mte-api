package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StrategicBetRepository extends JpaRepository<StrategicBet, Long> {
    boolean existsByNameIgnoreCase(String name);

    Optional<StrategicBet> findByNameIgnoreCase(String name);

    boolean existsByWorldId(Long worldId);
}
