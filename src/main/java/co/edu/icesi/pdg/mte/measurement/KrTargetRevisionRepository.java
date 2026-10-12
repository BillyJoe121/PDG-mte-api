package co.edu.icesi.pdg.mte.measurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface KrTargetRevisionRepository extends JpaRepository<KrTargetRevision, Long> {
    List<KrTargetRevision> findByKeyResultId(Long keyResultId);
}
