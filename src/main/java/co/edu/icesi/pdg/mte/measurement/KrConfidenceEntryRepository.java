package co.edu.icesi.pdg.mte.measurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface KrConfidenceEntryRepository extends JpaRepository<KrConfidenceEntry, Long> {
    List<KrConfidenceEntry> findByKeyResultIdOrderByRecordedAtDesc(Long keyResultId);
}
