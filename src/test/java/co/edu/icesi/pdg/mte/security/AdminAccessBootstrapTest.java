package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.api.dto.SecurityDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import co.edu.icesi.pdg.mte.security.credentials.CredentialService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminAccessBootstrapTest {
    private final ProfessorRepository professors = mock(ProfessorRepository.class);
    private final CredentialService credentials = mock(CredentialService.class);
    private final AccountAccessService accountAccess = mock(AccountAccessService.class);
    private final AdminAccessBootstrap bootstrap = new AdminAccessBootstrap(professors, credentials, accountAccess);

    @Test
    void issuesALinkForEachAdministratorWhileNoneHasAPassword() {
        when(professors.findAllByAccessRoleAndActiveTrueOrderByIdAsc("ADMIN")).thenReturn(List.of(admin(1L, null), admin(2L, UUID.randomUUID())));
        when(accountAccess.issueAccessLink(1L)).thenReturn(new SecurityDtos.AccessLinkResponse(1L, "a@icesi.edu.co", "url-1", true));
        when(accountAccess.issueAccessLink(2L)).thenThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "sin url"));

        assertThat(bootstrap.issueLinks()).isEqualTo(1);
        verify(accountAccess).issueAccessLink(2L);
    }

    @Test
    void doesNothingOnceAnAdministratorHasAPasswordOrThereIsNone() {
        UUID account = UUID.randomUUID();
        when(professors.findAllByAccessRoleAndActiveTrueOrderByIdAsc("ADMIN")).thenReturn(List.of(admin(1L, null), admin(2L, account)));
        when(credentials.hasPassword(account)).thenReturn(true);
        assertThat(bootstrap.issueLinks()).isZero();

        when(professors.findAllByAccessRoleAndActiveTrueOrderByIdAsc("ADMIN")).thenReturn(List.of());
        assertThat(bootstrap.issueLinks()).isZero();
        verify(accountAccess, never()).issueAccessLink(any());
    }

    private static Professor admin(Long id, UUID authUserId) {
        Professor professor = new Professor();
        professor.setId(id);
        professor.setEmail("admin" + id + "@icesi.edu.co");
        professor.setAccessRole("ADMIN");
        professor.setAuthUserId(authUserId);
        return professor;
    }
}
