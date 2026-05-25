package co.edu.icesi.pdg.mte.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

@Component
public class RequestTimingFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTimingFilter.class);
    private final SqlQueryCounter sqlQueryCounter;

    public RequestTimingFilter(SqlQueryCounter sqlQueryCounter) {
        this.sqlQueryCounter = sqlQueryCounter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        sqlQueryCounter.begin();
        try {
            filterChain.doFilter(request, response);
        } finally {
            double durationMs = (System.nanoTime() - startedAt) / 1_000_000.0;
            int sqlCount = sqlQueryCounter.count();
            response.setHeader("Server-Timing", "app;dur=" + String.format(Locale.ROOT, "%.1f", durationMs)
                    + ", sql;desc=\"queries\";dur=" + sqlCount);
            response.setHeader("X-SQL-Query-Count", String.valueOf(sqlCount));
            LOGGER.info(
                    "http_request method={} path={} status={} durationMs={} sqlCount={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    String.format(Locale.ROOT, "%.1f", durationMs),
                    sqlCount
            );
            sqlQueryCounter.clear();
        }
    }
}
