1) Создай БД в pgAdmin:
   CREATE DATABASE "rea.ohyeahworld.newpgapp";

2) Открой Query Tool именно этой БД.

3) При необходимости один раз очисти старую попытку:
   DROP TABLE IF EXISTS payments CASCADE;
   DROP TABLE IF EXISTS charges CASCADE;
   DROP TABLE IF EXISTS saldo CASCADE;

4) Можно выполнить database/setup.sql целиком.

5) В приложении укажи логин/пароль PostgreSQL в src/main/resources/application.properties.
