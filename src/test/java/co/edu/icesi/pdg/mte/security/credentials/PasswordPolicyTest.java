package co.edu.icesi.pdg.mte.security.credentials;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {
    private static final String EMAIL = "maria.lopez@icesi.edu.co";

    @Test
    void acceptsLongUncommonPasswords() {
        assertThat(PasswordPolicy.accepts("Trazabilidad-2026", EMAIL)).isTrue();
        assertThat(PasswordPolicy.accepts("ñandú-río-2026", EMAIL)).isTrue();
        assertThat(PasswordPolicy.accepts("Trazabilidad-2026", null)).isTrue();
    }

    @Test
    void rejectsShortCommonRepeatedAndEmailBasedPasswords() {
        assertThat(PasswordPolicy.accepts(null, EMAIL)).isFalse();
        assertThat(PasswordPolicy.accepts("Corta-12", EMAIL)).isFalse();
        assertThat(PasswordPolicy.accepts("Contrasena", EMAIL)).isFalse();
        assertThat(PasswordPolicy.accepts("1234567890", EMAIL)).isFalse();
        assertThat(PasswordPolicy.accepts("aaaaaaaaaaaa", EMAIL)).isFalse();
        assertThat(PasswordPolicy.accepts("Maria.Lopez-2026", EMAIL)).isFalse();
    }

    @Test
    void shortEmailNamesDoNotBlockPasswords() {
        assertThat(PasswordPolicy.accepts("ana-trazabilidad", "ana@icesi.edu.co")).isTrue();
    }

    @Test
    void countsCharactersForTheMinimumAndBytesForTheMaximum() {
        assertThat(PasswordPolicy.accepts("ñ".repeat(9) + "x", EMAIL)).isTrue();
        assertThat(PasswordPolicy.accepts("Trazabilidad-" + "x".repeat(59), EMAIL)).isTrue();
        assertThat(PasswordPolicy.exceedsMaxBytes("x".repeat(72))).isFalse();
        assertThat(PasswordPolicy.exceedsMaxBytes("ñ".repeat(37))).isTrue();
        assertThat(PasswordPolicy.accepts("ñ".repeat(37), EMAIL)).isFalse();
        assertThat(PasswordPolicy.exceedsMaxBytes(null)).isFalse();
    }
}
