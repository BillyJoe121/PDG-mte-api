package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriodRepository;

import java.time.LocalDate;

final class StrategyPeriodScope {
    private StrategyPeriodScope() {
    }

    static AcademicPeriod resolve(String periodName, AcademicPeriodRepository repository) {
        if (periodName == null || periodName.isBlank()) {
            return null;
        }
        return repository.findByNameIgnoreCase(periodName.trim()).orElse(null);
    }

    static boolean matches(StrategicBet bet, AcademicPeriod period) {
        return period == null || overlaps(bet.getStartDate(), bet.getEndDate(), period);
    }

    static boolean matches(InstitutionalGoal goal, AcademicPeriod period) {
        if (period == null) {
            return true;
        }
        if (!goal.getPeriods().isEmpty()) {
            return goal.getPeriods().stream().anyMatch(item -> item.getId().equals(period.getId()));
        }
        return overlaps(goal.getStartDate(), goal.getEndDate(), period);
    }

    static boolean matches(Objective objective, AcademicPeriod period) {
        return period == null || objective.getAcademicPeriod().getId().equals(period.getId());
    }

    private static boolean overlaps(LocalDate startDate, LocalDate endDate, AcademicPeriod period) {
        return !endDate.isBefore(period.getStartDate()) && !startDate.isAfter(period.getEndDate());
    }
}
