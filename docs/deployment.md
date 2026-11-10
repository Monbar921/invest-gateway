# Запуск и деплой invest-gateway

## Локальный запуск

Нужны JDK 25, Maven, запущенные Keycloak (realm `invest`) и invest-api.

```bash
mvn spring-boot:run
```

Запускать из корня репозитория: `config/application.yml` ищется относительно рабочей директории.

| Сервис      | Адрес по умолчанию                          |
| ----------- | ------------------------------------------- |
| gateway     | http://127.0.0.1:8082 (`server.port`)       |
| invest-api  | http://127.0.0.1:8081                       |
| Keycloak    | http://localhost:8180/realms/invest         |
| UI (Vite)   | http://localhost:5173, проксирует на gateway |

> Keycloak для локального запуска раньше поднимался `docker compose` из этого репозитория с realm из
> `keycloak/import/invest-realm.json`, но этих файлов в репозитории нет. Готовый realm (клиент `invest-gateway`,
> тестовый пользователь) лежит в [invest-deploy/keycloak/invest-realm.json](../../invest-deploy/keycloak/invest-realm.json) -
> для локального Keycloak в него нужно добавить redirect URI `http://localhost:5173/login/oauth2/code/keycloak`.

## Конфигурация

`config/application.yml` (Spring читает его из `./config` рабочей директории):

| Свойство / переменная                                    | Что                                     | По умолчанию                  |
| -------------------------------------------------------- | --------------------------------------- | ----------------------------- |
| `invest.gateway.invest-api-uri` / `INVEST_GATEWAY_INVEST_API_URI` | куда проксировать `/api/bonds/**` | `http://127.0.0.1:8081`       |
| `invest.keycloak.issuer-uri` / `INVEST_KEYCLOAK_ISSUER_URI` | issuer realm                         | `http://localhost:8180/realms/invest` |
| `KEYCLOAK_CLIENT_SECRET`                                 | секрет клиента `invest-gateway`         | dev-значение `invest-gateway-dev-secret` |
| `invest.gateway.public-access.enabled` / `INVEST_GATEWAY_PUBLIC_ACCESS_ENABLED` | чтение облигаций без входа | `true` |
| `invest.gateway.public-access.rate-limit.requests-per-minute` | анонимных запросов к `/api/**` в минуту с одного IP, `0` - без лимита | `120` |
| `invest.gateway.public-access.rate-limit.max-clients`    | сколько IP лимитер держит в памяти      | `10000` |

Issuer должен совпадать с адресом, по которому Keycloak видит браузер: Spring проверяет `iss` в токене.

Публичный режим на открытом адресе включать после проверки условий источников данных (решение 0.1 в
[дорожной карте](../../invest-api/docs/roadmap.md)). Лимит анонимных запросов считает IP по `X-Forwarded-For`:
ingress перед gateway должен выставлять этот заголовок сам, не доверяя присланному клиентом.

## Стенд в k3d

[invest-deploy](../../invest-deploy/docs/deployment.md): `.\deploy.ps1 gateway`. Профиль `k8s` (порт 8080, адрес
invest-api в кластере, issuer `http://keycloak.localhost:8088/realms/invest`) лежит в invest-deploy, базовый
`config/application.yml` берётся отсюда.

## Образ

`mvn package` раскладывает приложение в `app/target/dockerbuild` (`ext/` - сторонние библиотеки, `lib/` - модули),
запуск: `java -cp '/app/lib/*:/app/ext/*' ru.invest.gateway.InvestGatewayApplication`. Dockerfile -
`invest-deploy/docker/java.Dockerfile`. CI в репозитории нет.
