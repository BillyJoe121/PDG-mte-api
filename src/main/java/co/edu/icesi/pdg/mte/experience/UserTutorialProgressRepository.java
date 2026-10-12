package co.edu.icesi.pdg.mte.experience;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserTutorialProgressRepository extends JpaRepository<UserTutorialProgress, Long> {
    Optional<UserTutorialProgress> findByUserIdAndRoleContext(String userId, String roleContext);
}
