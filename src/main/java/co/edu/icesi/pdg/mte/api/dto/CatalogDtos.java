package co.edu.icesi.pdg.mte.api.dto;

import co.edu.icesi.pdg.mte.catalog.MeasurementUnitType;
import co.edu.icesi.pdg.mte.catalog.PeriodStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public final class CatalogDtos {
    private CatalogDtos() {
    }

    public record MeasurementUnitRequest(
            @NotBlank String name,
            @NotNull MeasurementUnitType type,
            String description
    ) {
    }

    public record MeasurementUnitResponse(
            Long id,
            String name,
            MeasurementUnitType type,
            String description,
            boolean active
    ) {
    }

    public record AcademicPeriodRequest(
            @NotBlank String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull PeriodStatus status
    ) {
    }

    public record AcademicPeriodResponse(
            Long id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            PeriodStatus status
    ) {
    }

    public record MeasurementUnitActiveRequest(
            @NotNull Boolean active
    ) {
    }

    public record AcademicPeriodStatusRequest(
            @NotNull PeriodStatus status
    ) {
    }

    public record AcademicPeriodActiveRequest(
            @NotNull Boolean active
    ) {
    }

    public record DepartmentResponse(
            Long id,
            String name,
            String description,
            Long externalDepartmentId
    ) {
    }
}
