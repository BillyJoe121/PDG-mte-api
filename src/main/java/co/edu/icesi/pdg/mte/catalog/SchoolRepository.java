package co.edu.icesi.pdg.mte.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {
    Optional<School> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
