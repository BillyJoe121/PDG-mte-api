package co.edu.icesi.pdg.mte.security;

/** Builds Supabase client errors for tests outside this package. */
public final class SupabaseErrors {
    private SupabaseErrors() {
    }

    public static SupabaseAuthClient.SupabaseException invalidCredentials() {
        return new SupabaseAuthClient.InvalidCredentialsException("invalid_credentials");
    }
}
