package co.edu.icesi.pdg.mte.experience;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserUiPreferenceRepository extends JpaRepository<UserUiPreference, Long> {
    Optional<UserUiPreference> findByUserId(String userId);
}
