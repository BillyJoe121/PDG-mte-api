package co.edu.icesi.pdg.mte.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SavedViewRepository extends JpaRepository<SavedView, Long> {
    List<SavedView> findByUserId(String userId);
}
