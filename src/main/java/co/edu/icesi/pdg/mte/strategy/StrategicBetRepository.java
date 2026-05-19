package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategicBetRepository extends JpaRepository<StrategicBet, Long> {
    boolean existsByNameIgnoreCase(String name);

    boolean existsByWorldId(Long worldId);
}
