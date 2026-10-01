# rea.ohyeahworld.newpgapp

Простая реализация лабораторной работы №3 на Java Spring Boot + Thymeleaf + PostgreSQL.

По заданию нужны: БД, таблицы `saldo`, `charges`, `payments`, WEB-интерфейс для заполнения и 3 отчетных модуля. В образцах из задания показаны оборотная ведомость за год, ведомость по квартире и сводка по категориям должников.

## 1. Что нужно установить

- Java 21
- PostgreSQL 14+ (подойдет и более новая версия)
- Maven 3.9+
- IntelliJ IDEA или другая IDE

## 2. База данных

Проект по умолчанию подключается к базе:

`rea.ohyeahworld.newpgapp`

Если базы еще нет, в pgAdmin Query Tool можно выполнить:

```sql
CREATE DATABASE "rea.ohyeahworld.newpgapp";
```

Пользователь по умолчанию: `postgres`

Пароль по умолчанию: `postgres`

Если у тебя другой пароль, меняется только пароль в `src/main/resources/application.properties`:

```properties
spring.datasource.username=postgres
spring.datasource.password=ТВОЙ_ПАРОЛЬ
```

При необходимости также можно задать переменные окружения:

```text
DB_URL=jdbc:postgresql://localhost:5432/rea.ohyeahworld.newpgapp
DB_USERNAME=postgres
DB_PASSWORD=ТВОЙ_ПАРОЛЬ
```

## 3. Запуск

Открой папку проекта в IntelliJ IDEA и запусти класс:

`rea.ohyeahworld.newpgapp.NewPgAppApplication`

Либо из терминала:

```bash
mvn spring-boot:run
```

После старта открой:

`http://localhost:8080`

При первом запуске приложение само создаст три таблицы и три SQL-функции. Если таблицы пустые, загрузятся демонстрационные данные по мотивам чисел из задания.

## 4. Страницы

- `/saldo` — ввод/изменение/удаление сальдо
- `/charges` — ввод/изменение/удаление начислений
- `/payments` — ввод/изменение/удаление платежей
- `/reports/turnover?year=2017` — оборотная ведомость за год
- `/reports/apartment?apartment=1&year=2017` — оборотная ведомость по квартире
- `/reports/debtors?asOf=2017-10-01` — сводка по категориям должников

## 5. Важный момент по заданию

В методичке сказано, что точный реквизитный состав таблиц нужно согласовать с преподавателем. Здесь выбран простой состав, достаточный для работы трех отчетов.

Три отчетные функции PostgreSQL:

- `fn_turnover_statement(year)`
- `fn_apartment_statement(apartment, year)`
- `fn_debtor_categories(as_of)`

Они вызываются из Java через `SELECT * FROM ...`.
