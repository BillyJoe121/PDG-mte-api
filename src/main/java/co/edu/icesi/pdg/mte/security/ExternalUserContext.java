package co.edu.icesi.pdg.mte.security;

import java.util.List;

public record ExternalUserContext(
        Long externalUserId,
        String username,
        String email,
        List<String> roles,
        List<String> permissions,
        Long externalProfessorId,
        String professorName,
        String departmentName
) {
    public static ExternalUserContext mock() {
        return new ExternalUserContext(
                1L,
                "demo.admin",
                "demo.admin@icesi.edu.co",
                List.of("ADMIN", "DIRECTOR_ESCUELA"),
                List.of("MTE_ADMIN", "MTE_WRITE", "MTE_READ"),
                1L,
                "Usuario Demo",
                "Departamento de Computaci\u00f3n y Sistemas inteligentes."
        );
    }
}
