package co.edu.icesi.pdg.mte.security;

import java.util.List;

/** Usuarios fijos para pruebas; el código de producción no tiene usuarios de demostración. */
public final class TestUsers {

    private TestUsers() {
    }

    public static ExternalUserContext admin() {
        return new ExternalUserContext(
                1L,
                "demo.admin",
                "demo.admin@icesi.edu.co",
                List.of("ADMIN"),
                List.of("MTE_ADMIN", "MTE_WRITE", "MTE_READ"),
                1L,
                "Usuario Demo",
                "Departamento de Computación y Sistemas inteligentes."
        );
    }
}
