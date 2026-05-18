package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class ProjectPeriodService {
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^(\\d{4})-(Q[1-4]|[1-2])$");

    PeriodRange parse(String period) {
        if (period == null || period.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El periodo debe tener formato YYYY-1, YYYY-2 o YYYY-QN.");
        }
        Matcher matcher = PERIOD_PATTERN.matcher(period);
        if (!matcher.matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El periodo debe tener formato YYYY-1, YYYY-2 o YYYY-QN.");
        }
        int year = Integer.parseInt(matcher.group(1));
        String term = matcher.group(2);
        int startQuarter;
        int endQuarter;
        if (term.startsWith("Q")) {
            startQuarter = Integer.parseInt(term.substring(1));
            endQuarter = startQuarter;
        } else if ("1".equals(term)) {
            startQuarter = 1;
            endQuarter = 2;
        } else {
            startQuarter = 3;
            endQuarter = 4;
        }
        int yearBase = year * 4;
        return new PeriodRange(yearBase + startQuarter, yearBase + endQuarter);
    }

    void validateRange(String startPeriod, String endPeriod) {
        PeriodRange start = parse(startPeriod == null ? null : startPeriod.trim());
        if (endPeriod == null || endPeriod.isBlank()) {
            return;
        }
        PeriodRange end = parse(endPeriod.trim());
        if (end.endIndex() < start.startIndex()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El periodo de fin debe ser igual o posterior al periodo de inicio.");
        }
    }

    boolean overlaps(Project project, PeriodRange requestedPeriod) {
        PeriodRange projectStart = parse(project.getStartPeriod());
        PeriodRange projectEnd = project.getEndPeriod() == null || project.getEndPeriod().isBlank()
                ? projectStart
                : parse(project.getEndPeriod().trim());
        int projectStartIndex = projectStart.startIndex();
        int projectEndIndex = Math.max(projectStart.endIndex(), projectEnd.endIndex());
        return projectStartIndex <= requestedPeriod.endIndex() && requestedPeriod.startIndex() <= projectEndIndex;
    }

    record PeriodRange(int startIndex, int endIndex) {
    }
}
