package co.edu.icesi.pdg.mte.security.credentials;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuthCredentialRepository extends JpaRepository<AuthCredential, UUID> {
}
