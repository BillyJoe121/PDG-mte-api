package co.edu.icesi.pdg.mte.security.credentials;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Rules a new password must meet: 10 characters or more, at most 72 bytes (the BCrypt limit),
 * not a well-known password, not one repeated character and not built from the email name.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    private static final Set<String> COMMON = Set.of(
            "1234567890", "0123456789", "12345678910", "1234567890a", "123456789a",
            "qwertyuiop", "asdfghjkl1", "password123", "password1234", "contrasena",
            "contraseña", "contrasena1", "contraseña1", "contrasena123", "contraseña123",
            "administrador", "icesi12345", "icesi123456", "universidad", "bienvenido",
            "bienvenido1", "abcdefghij", "abc1234567", "iloveyou12", "colombia123"
    );

    private PasswordPolicy() {
    }

    public static boolean exceedsMaxBytes(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }

    /** True when the password may be stored for the person with this email. */
    public static boolean accepts(String password, String email) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH || exceedsMaxBytes(password)) {
            return false;
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower) || lower.chars().distinct().count() == 1) {
            return false;
        }
        String name = emailName(email);
        return name.length() < 4 || !lower.contains(name);
    }

    private static String emailName(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        return (at < 0 ? email : email.substring(0, at)).trim().toLowerCase(Locale.ROOT);
    }
}
