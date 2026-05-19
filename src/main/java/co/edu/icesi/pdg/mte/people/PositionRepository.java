package co.edu.icesi.pdg.mte.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {
    Optional<Position> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
