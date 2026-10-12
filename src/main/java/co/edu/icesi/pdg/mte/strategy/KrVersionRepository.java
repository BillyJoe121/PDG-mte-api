package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface KrVersionRepository extends JpaRepository<KrVersion, Long> {
    List<KrVersion> findByKeyResultIdOrderByVersionDesc(Long keyResultId);
    Optional<KrVersion> findByKeyResultIdAndVersion(Long keyResultId, Long version);
}
