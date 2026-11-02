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
}
