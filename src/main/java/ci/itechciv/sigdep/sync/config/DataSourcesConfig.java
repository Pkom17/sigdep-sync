package ci.itechciv.sigdep.sync.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DataSourcesConfig {

    @Bean
    @Primary
    @ConfigurationProperties("sigdep.sync.local-db")
    public DataSource localDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties("sigdep.sync.buffer-db")
    public DataSource bufferDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public JdbcTemplate localJdbcTemplate(@Qualifier("localDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    public JdbcTemplate bufferJdbcTemplate(@Qualifier("bufferDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
