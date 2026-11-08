# invest-gateway

Единая точка входа для [invest-api-ui](../invest-api-ui) и [invest-api](../invest-api): Spring Cloud Gateway в роли BFF.
Сам проходит OAuth2-логин в Keycloak и держит токены в HTTP-сессии, браузер получает только session cookie.

```bash
mvn spring-boot:run     # http://127.0.0.1:8082, нужны Keycloak (realm invest) и invest-api
```

Документация - в [docs/](docs/README.md): архитектура, тесты, запуск и деплой.
