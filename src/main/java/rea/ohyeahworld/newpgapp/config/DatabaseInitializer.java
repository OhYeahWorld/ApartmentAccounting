package rea.ohyeahworld.newpgapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class DatabaseInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final boolean demoDataEnabled;

    public DatabaseInitializer(JdbcTemplate jdbc,
                               @Value("${app.demo-data.enabled:true}") boolean demoDataEnabled) {
        this.jdbc = jdbc;
        this.demoDataEnabled = demoDataEnabled;
    }

    @Override
    public void run(String... args) {
        runScript("db/schema.sql");
        runFunction("db/functions/fn_turnover_statement.sql");
        runFunction("db/functions/fn_apartment_statement.sql");
        runFunction("db/functions/fn_debtor_categories.sql");

        if (demoDataEnabled && isEmpty()) {
            runScript("db/data.sql");
        }
    }

    private void runScript(String path) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.addScript(new ClassPathResource(path));
        populator.execute(jdbc.getDataSource());
    }

    private void runFunction(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            jdbc.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось создать SQL-функцию: " + path, e);
        }
    }

    private boolean isEmpty() {
        Long saldo = jdbc.queryForObject("SELECT COUNT(*) FROM saldo", Long.class);
        Long charges = jdbc.queryForObject("SELECT COUNT(*) FROM charges", Long.class);
        Long payments = jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long.class);
        return (saldo == null ? 0 : saldo)
                + (charges == null ? 0 : charges)
                + (payments == null ? 0 : payments) == 0;
    }
}
