package co.edu.icesi.pdg.mte.common;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateQueryCountingConfig {
    @Bean
    HibernatePropertiesCustomizer queryCountingCustomizer(SqlQueryCountingStatementInspector inspector) {
        return properties -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, inspector);
    }
}
