package co.edu.icesi.pdg.mte;

import co.edu.icesi.pdg.mte.catalog.*;
import co.edu.icesi.pdg.mte.strategy.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class TestFixtures {
    private TestFixtures() {
    }

    public static MeasurementUnit unit(Long id) {
        MeasurementUnit unit = new MeasurementUnit();
        unit.setId(id);
        unit.setName("Porcentaje");
        unit.setType(MeasurementUnitType.PORCENTAJE);
        unit.setDescription("Unidad de prueba");
        return unit;
    }

    public static AcademicPeriod period(Long id) {
        AcademicPeriod period = new AcademicPeriod();
        period.setId(id);
        period.setName("2026-1");
        period.setStartDate(LocalDate.of(2026, 1, 1));
        period.setEndDate(LocalDate.of(2026, 6, 30));
        period.setStatus(PeriodStatus.ACTIVO);
        return period;
    }

    public static Department department(Long id) {
        Department department = new Department();
        department.setId(id);
        department.setName("Departamento de Computaci\u00f3n y Sistemas inteligentes.");
        department.setDescription("Departamento de prueba");
        department.setExternalDepartmentId(10L);
        department.setSchool(school(1L));
        return department;
    }

    public static School school(Long id) {
        School school = new School();
        school.setId(id);
        school.setName("Escuela TDI");
        school.setDescription("Escuela de prueba");
        return school;
    }

    public static World world(Long id) {
        World world = new World();
        world.setId(id);
        world.setName("Mundo");
        world.setDescription("Mundo de prueba");
        return world;
    }

    public static StrategicBet strategicBet(Long id) {
        StrategicBet bet = new StrategicBet();
        bet.setId(id);
        bet.setName("Apuesta");
        bet.setDescription("Descripcion de apuesta");
        bet.setStartDate(LocalDate.of(2026, 1, 1));
        bet.setEndDate(LocalDate.of(2026, 12, 31));
        bet.setWorld(world(1L));
        return bet;
    }

    public static InstitutionalGoal goal(Long id, MeasurementUnit unit) {
        InstitutionalGoal goal = new InstitutionalGoal();
        goal.setId(id);
        goal.setName("Meta");
        goal.setDescription("Descripcion de meta");
        goal.setExpectedValue(BigDecimal.valueOf(80));
        goal.setStartDate(LocalDate.of(2026, 1, 1));
        goal.setEndDate(LocalDate.of(2026, 12, 31));
        goal.setWorld(world(1L));
        goal.setMeasurementUnit(unit);
        return goal;
    }

    public static KeyResult keyResult(
            Long id,
            MeasurementUnit unit,
            AcademicPeriod period,
            InstitutionalGoal goal,
            StrategicBet bet
    ) {
        KeyResult keyResult = keyResult(id, unit);
        keyResult.setAcademicPeriod(period);
        if (goal != null) {
            keyResult.getGoals().add(goal);
        }
        if (bet != null) {
            keyResult.getStrategicBets().add(bet);
        }
        return keyResult;
    }

    public static KeyResult keyResult(Long id, MeasurementUnit unit) {
        KeyResult keyResult = new KeyResult();
        keyResult.setId(id);
        keyResult.setName("KR");
        keyResult.setDescription("KR");
        keyResult.setMetric("Porcentaje");
        keyResult.setBaseValue(BigDecimal.ZERO);
        keyResult.setTargetValue(BigDecimal.valueOf(100));
        keyResult.setCurrentValue(BigDecimal.valueOf(25));
        keyResult.setStatus(KeyResultStatus.ACTIVO);
        keyResult.setMeasurementUnit(unit);
        keyResult.recalculateProgress();
        return keyResult;
    }
}
