package rea.ohyeahworld.newpgapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            jdbc.execute("DROP FUNCTION IF EXISTS fn_month_movement(INTEGER, DATE) CASCADE");
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
            Integer cols = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_name = 'saldo' AND column_name IN ('created_at', 'updated_at')
                    """, Integer.class);
            if (cols == null || cols < 2) {
                return true;
            }
            // старый вариант таблицы без CHECK-формулы закрытия периода
            Integer checks = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.check_constraints
                    WHERE constraint_name = 'ck_saldo_closing_formula'
                    """, Integer.class);
            return checks == null || checks == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void runScript(String path) {
        String sql = readResource(path);
        for (String statement : splitSqlStatements(sql)) {
            try {
                jdbc.execute(statement);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Не удалось выполнить SQL-скрипт " + path + ":\n" + statement, e);
            }
        }
    }

    private void runFunction(String path) {
        try {
            jdbc.execute(readResource(path));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось создать SQL-функцию: " + path, e);
        }
    }

    private String readResource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать SQL-скрипт: " + path, e);
        }
    }

    /**
     * Разбивает SQL-скрипт на выражения по точке с запятой, НЕ разрывая
     * dollar-quoted блоки ($$ ... $$), строковые литералы ('...') и кавычки
     * идентификаторов ("..."). Spring ScriptUtils так не умеет и режет
     * тела plpgsql-функций посередине.
     */
    static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        Pattern tagAt = Pattern.compile("(\\$[A-Za-z_][A-Za-z0-9_]*\\$)");
        int i = 0;
        int start = 0;
        int len = sql.length();
        while (i < len) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"') {
                char quote = c;
                i++;
                while (i < len) {
                    if (sql.charAt(i) == quote) {
                        // '' / "" — экранированная кавычка
                        if (i + 1 < len && sql.charAt(i + 1) == quote) {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
            } else if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                // комментарий до конца строки
                while (i < len && sql.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '$') {
                // dollar-quoted блок: $$...$$ или $tag$...$tag$
                String tag;
                int bodyStart;
                if (i + 1 < len && sql.charAt(i + 1) == '$') {
                    tag = "$$";
                    bodyStart = i + 2;
                } else {
                    Matcher m = tagAt.matcher(sql);
                    if (m.find(i) && m.start() == i) {
                        tag = m.group(1);
                        bodyStart = m.end();
                    } else {
                        tag = null;
                        bodyStart = -1;
                    }
                }
                if (tag != null) {
                    int end = sql.indexOf(tag, bodyStart);
                    if (end < 0) {
                        throw new IllegalStateException(
                                "Незакрытый dollar-quoted блок: " + tag);
                    }
                    i = end + tag.length();
                } else {
                    i++;
                }
            } else if (c == ';') {
                String stmt = sql.substring(start, i).trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                i++;
                start = i;
            } else {
                i++;
            }
        }
        String tail = sql.substring(start).trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
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
