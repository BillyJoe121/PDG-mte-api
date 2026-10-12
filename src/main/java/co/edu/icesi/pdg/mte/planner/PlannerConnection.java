package co.edu.icesi.pdg.mte.planner;

import co.edu.icesi.pdg.mte.people.Professor;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "planner_connection")
public class PlannerConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_id")
    private Professor professor;

    @Column(nullable = false, length = 32)
    private String provider = "MICROSOFT_GRAPH";

    @Column(name = "external_tenant_id", nullable = false, columnDefinition = "text")
    private String externalTenantId;

    @Column(name = "granted_scopes", nullable = false, columnDefinition = "text")
    private String grantedScopes;

    @Column(name = "consent_status", nullable = false, length = 24)
    private String consentStatus = "ACTIVE";

    @Column(name = "secret_ref", nullable = false, columnDefinition = "text")
    private String secretRef;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Professor getProfessor() { return professor; }
    public void setProfessor(Professor professor) { this.professor = professor; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getExternalTenantId() { return externalTenantId; }
    public void setExternalTenantId(String externalTenantId) { this.externalTenantId = externalTenantId; }

    public String getGrantedScopes() { return grantedScopes; }
    public void setGrantedScopes(String grantedScopes) { this.grantedScopes = grantedScopes; }

    public String getConsentStatus() { return consentStatus; }
    public void setConsentStatus(String consentStatus) { this.consentStatus = consentStatus; }

    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }

    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
