package co.edu.icesi.pdg.mte.measurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface KrProgressEntryRepository extends JpaRepository<KrProgressEntry, Long> {
    List<KrProgressEntry> findByKeyResultIdOrderByDeclaredAtDesc(Long keyResultId);
}
