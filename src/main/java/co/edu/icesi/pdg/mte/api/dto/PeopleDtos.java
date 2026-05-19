package co.edu.icesi.pdg.mte.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class PeopleDtos {
    private PeopleDtos() {
    }

    public record RoleRequest(
            @NotBlank String name,
            String description
    ) {
    }

    public record RoleResponse(
            Long id,
            String name,
            String description
    ) {
    }

    public record PositionRequest(
            @NotBlank String name,
            String description
    ) {
    }

    public record PositionResponse(
            Long id,
            String name,
            String description
    ) {
    }

    public record ProfessorRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotNull Long departmentId
    ) {
    }

    public record ProfessorResponse(
            Long id,
            String name,
            String email,
            Long departmentId,
            String departmentName
    ) {
    }

    public record TeacherPositionRequest(
            @NotNull Long positionId,
            Boolean active
    ) {
    }

    public record TeacherPositionResponse(
            Long positionId,
            String positionName,
            Long teacherId,
            String teacherName,
            Boolean active
    ) {
    }
}
