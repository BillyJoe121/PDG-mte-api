package co.edu.icesi.pdg.mte.security.credentials;

import co.edu.icesi.pdg.mte.security.AuthProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CredentialServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T15:00:00Z");
    private static final UUID USER = UUID.fromString("8f9c3a4e-2b1d-4c5e-9f00-112233445566");
    private static final String EMAIL = "persona@icesi.edu.co";
    private static final String PASSWORD = "Trazabilidad-2026";

    private final AuthCredentialRepository credentials = mock(AuthCredentialRepository.class);
    private final AuthLinkTokenRepository links = mock(AuthLinkTokenRepository.class);
    private final AuthProperties properties = new AuthProperties(null, null, null, Duration.ofHours(72), null, false);
    private final CredentialService service =
            new CredentialService(credentials, links, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void storedPasswordsAreHashedAndVerified() {
        AuthCredential credential = new AuthCredential(USER, NOW);
        when(credentials.findById(USER)).thenReturn(Optional.of(credential));

        service.setPassword(USER, PASSWORD, EMAIL);

        assertThat(credential.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, credential.getPasswordHash())).isTrue();
        service.verifyPassword(USER, PASSWORD);
        assertThatThrownBy(() -> service.verifyPassword(USER, "Otra-clave-2026"))
                .isInstanceOf(CredentialService.InvalidCredentialsException.class);
        assertThat(service.hasPassword(USER)).isTrue();
    }

    @Test
    void accountsWithoutPasswordOrIdAndOverlongPasswordsAreRefused() {
        when(credentials.findById(USER)).thenReturn(Optional.of(new AuthCredential(USER, NOW)));

        assertThatThrownBy(() -> service.verifyPassword(USER, PASSWORD))
                .isInstanceOf(CredentialService.InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.verifyPassword(null, PASSWORD))
                .isInstanceOf(CredentialService.InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.verifyPassword(USER, "a".repeat(73)))
                .isInstanceOf(CredentialService.InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.verifyPassword(USER, null))
                .isInstanceOf(CredentialService.InvalidCredentialsException.class);
        assertThat(service.hasPassword(USER)).isFalse();
        assertThat(service.hasPassword(null)).isFalse();
    }

    @Test
    void weakPasswordsAndMissingAccountsAreNotStored() {
        assertThatThrownBy(() -> service.setPassword(USER, "contrasena", EMAIL))
                .isInstanceOf(CredentialService.RejectedException.class);
        when(credentials.findById(USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setPassword(USER, PASSWORD, EMAIL))
                .isInstanceOf(CredentialService.InvalidCredentialsException.class);
        verify(credentials, never()).save(any());
    }

    @Test
    void ensureAccountKeepsKnownIdsAndCreatesMissingAccounts() {
        when(credentials.existsById(USER)).thenReturn(true);
        assertThat(service.ensureAccount(USER)).isEqualTo(new CredentialService.Account(USER, false));
        verify(credentials, never()).save(any());

        UUID previous = UUID.randomUUID();
        assertThat(service.ensureAccount(previous)).isEqualTo(new CredentialService.Account(previous, true));

        CredentialService.Account created = service.ensureAccount(null);
        assertThat(created.created()).isTrue();
        assertThat(created.userId()).isNotNull().isNotEqualTo(previous);
        verify(credentials, times(2)).save(any(AuthCredential.class));
    }

    @Test
    void issuedLinksStoreOnlyTheHashAndReplaceEarlierOnes() {
        String token = service.issueLink(USER, "invite");

        ArgumentCaptor<AuthLinkToken> saved = ArgumentCaptor.forClass(AuthLinkToken.class);
        verify(links).deleteExpiredBefore(NOW);
        verify(links).supersedeUnused(USER, NOW);
        verify(links).save(saved.capture());
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(saved.getValue().getTokenHash()).isEqualTo(CredentialService.sha256(token)).isNotEqualTo(token);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(72)));
        assertThat(service.issueLink(USER, "invite")).isNotEqualTo(token);
    }

    @Test
    void aValidLinkIsUsedOnce() {
        AuthLinkToken link = link("invite", NOW.plusSeconds(60));
        when(links.findByTokenHash(CredentialService.sha256("token"))).thenReturn(Optional.of(link));
        when(links.markUsed(any(), any())).thenReturn(1, 0);

        assertThat(service.consumeLink("invite", "token")).isEqualTo(USER);
        assertThatThrownBy(() -> service.consumeLink("invite", "token"))
                .isInstanceOf(CredentialService.InvalidCredentialsException.class);
    }

    @Test
    void unknownMismatchedUsedAndExpiredLinksFailTheSameWay() {
        when(links.findByTokenHash(CredentialService.sha256("tipo"))).thenReturn(Optional.of(link("invite", NOW.plusSeconds(60))));
        when(links.findByTokenHash(CredentialService.sha256("vencido"))).thenReturn(Optional.of(link("invite", NOW)));
        AuthLinkToken used = link("invite", NOW.plusSeconds(60));
        ReflectionTestUtils.setField(used, "usedAt", NOW);
        when(links.findByTokenHash(CredentialService.sha256("usado"))).thenReturn(Optional.of(used));

        for (String token : new String[]{null, "no-existe", "tipo", "vencido", "usado"}) {
            String type = "tipo".equals(token) ? "recovery" : "invite";
            assertThatThrownBy(() -> service.consumeLink(type, token))
                    .isInstanceOf(CredentialService.InvalidCredentialsException.class);
        }
        verify(links, never()).markUsed(any(), any());
    }

    private static AuthLinkToken link(String type, Instant expiresAt) {
        return new AuthLinkToken("hash", USER, type, NOW.minusSeconds(60), expiresAt);
    }
}
