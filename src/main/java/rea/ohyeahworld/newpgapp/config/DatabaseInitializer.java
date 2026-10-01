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
        // Старые таблицы без created_at/updated_at и новых ограничений —
        // пересоздаем схему вместе с триггерами (CREATE OR REPLACE не помогает
        // изменить столбцы и CHECK-ограничения).
        if (needsRebuild()) {
            jdbc.execute("""
                    DROP TABLE IF EXISTS payments CASCADE;
                    DROP TABLE IF EXISTS charges CASCADE;
                    DROP TABLE IF EXISTS saldo CASCADE;
                    """);
            jdbc.execute("DROP FUNCTION IF EXISTS fn_next_opening_balance(INTEGER, DATE) CASCADE");
            jdbc.execute("DROP FUNCTION IF EXISTS fn_recalc_saldo_chain(INTEGER) CASCADE");
            jdbc.execute("DROP FUNCTION IF EXISTS trg_saldo_before_write() CASCADE");
            jdbc.execute("DROP FUNCTION IF EXISTS trg_saldo_after_write() CASCADE");
            jdbc.execute("DROP FUNCTION IF EXISTS trg_charges_after_write() CASCADE");
            jdbc.execute("DROP FUNCTION IF EXISTS trg_payments_after_write() CASCADE");
            jdbc.execute("DROP FUNCTION IF EXISTS trg_charges_normalize_period() CASCADE");
        }

        runScript("db/schema.sql");
        runFunction("db/functions/fn_next_opening_balance.sql");
        runFunction("db/functions/fn_turnover_statement.sql");
        runFunction("db/functions/fn_apartment_statement.sql");
        runFunction("db/functions/fn_debtor_categories.sql");

        if (demoDataEnabled && isEmpty()) {
            runScript("db/data.sql");
        }
    }

    private boolean needsRebuild() {
        try {
            Integer n = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_name = 'saldo' AND column_name IN ('created_at', 'updated_at')
                    """, Integer.class);
            return n != null && n < 2;
        } catch (Exception e) {
            return false;
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
