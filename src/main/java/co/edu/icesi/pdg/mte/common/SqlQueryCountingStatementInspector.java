package co.edu.icesi.pdg.mte.common;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.stereotype.Component;

@Component
public class SqlQueryCountingStatementInspector implements StatementInspector {
    private final SqlQueryCounter counter;

    public SqlQueryCountingStatementInspector(SqlQueryCounter counter) {
        this.counter = counter;
    }

    @Override
    public String inspect(String sql) {
        counter.increment();
        return sql;
    }
}
