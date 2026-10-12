package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GuardrailRepository extends JpaRepository<Guardrail, Long> {
    List<Guardrail> findByActiveTrue();
}
