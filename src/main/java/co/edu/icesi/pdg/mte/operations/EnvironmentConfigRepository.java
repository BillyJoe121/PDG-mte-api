package co.edu.icesi.pdg.mte.operations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EnvironmentConfigRepository extends JpaRepository<EnvironmentConfig, Long> {
    Optional<EnvironmentConfig> findByEnvironmentName(String environmentName);
}
