package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import co.edu.icesi.pdg.mte.security.credentials.CredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * First sign-in on an empty installation. Only administrators can issue access links, so a new
 * database would have nobody able to sign in. With {@code MTE_AUTH_BOOTSTRAP_ADMIN_LINK=true},
 * and only while no administrator has a password, each active administrator gets a single-use
 * link written to the log at startup. Turn it off once an administrator has signed in.
 */
@Component
@ConditionalOnProperty(prefix = "mte.auth", name = "bootstrap-admin-link", havingValue = "true")
public class AdminAccessBootstrap {
    static final String ADMIN = "ADMIN";
    private static final Logger log = LoggerFactory.getLogger(AdminAccessBootstrap.class);

    private final ProfessorRepository professors;
    private final CredentialService credentials;
    private final AccountAccessService accountAccess;

    public AdminAccessBootstrap(
            ProfessorRepository professors,
            CredentialService credentials,
            AccountAccessService accountAccess
    ) {
        this.professors = professors;
        this.credentials = credentials;
        this.accountAccess = accountAccess;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        issueLinks();
    }

    /** Returns how many links were written to the log. */
    int issueLinks() {
        List<Professor> admins = professors.findAllByAccessRoleAndActiveTrueOrderByIdAsc(ADMIN);
        if (admins.isEmpty()) {
            log.warn("Initial access: there is no active administrator, no link was issued.");
            return 0;
        }
        if (admins.stream().anyMatch(admin -> credentials.hasPassword(admin.getAuthUserId()))) {
            log.info("Initial access: an administrator already has a password; set MTE_AUTH_BOOTSTRAP_ADMIN_LINK=false.");
            return 0;
        }
        int issued = 0;
        for (Professor admin : admins) {
            try {
                SecurityDtos.AccessLinkResponse link = accountAccess.issueAccessLink(admin.getId());
                log.warn("Initial access link for {} (single use, deliver it privately): {}", link.email(), link.url());
                issued++;
            } catch (RuntimeException exception) {
                log.error("Initial access: no link for {}: {}", admin.getEmail(), exception.getMessage());
            }
        }
        return issued;
    }
}
