package co.edu.icesi.pdg.mte.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, Long> {
    Optional<RetentionPolicy> findByEntityScopeAndStatus(String entityScope, String status);
}
