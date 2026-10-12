package co.edu.icesi.pdg.mte.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface InstitutionalPolicyRepository extends JpaRepository<InstitutionalPolicy, Long> {
    Optional<InstitutionalPolicy> findFirstByPolicyKeyOrderByVersionDesc(String policyKey);
}
