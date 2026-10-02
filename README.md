# invest-gateway

Единая точка входа для [invest-api-ui](../invest-api-ui) и [invest-api](../invest-api).
Spring Cloud Gateway (Server WebMVC) в роли BFF: gateway сам проходит OAuth2-логин в Keycloak и хранит токены
в HTTP-сессии. Браузер получает только session cookie, invest-api токенов не видит и доступен только через gateway.

```
браузер ── :5173 Vite (dev) ──proxy──▶ :8080 invest-gateway ──▶ 127.0.0.1:8081 invest-api
                                              │
                                              └── OIDC ──▶ :8180 Keycloak (realm invest)
```

## Запуск

```bash
docker compose up -d
mvn spring-boot:run
```

Затем invest-api (слушает `127.0.0.1:8081`) и `npm run dev` в invest-api-ui. UI открывается на http://localhost:5173,
при первом запросе к API перекидывает на логин Keycloak.

- Тестовый пользователь, клиент `invest-gateway` и его dev-секрет описаны в `keycloak/import/invest-realm.json`.
- Админка Keycloak: http://localhost:8180, учётка - в `docker-compose.yml`.
- Realm импортируется только при создании контейнера: после правок JSON - `docker compose down && docker compose up -d`.

Вне локального стенда секрет клиента задаётся через `KEYCLOAK_CLIENT_SECRET`, адреса - через
`INVEST_GATEWAY_INVEST_API_URI` и `INVEST_KEYCLOAK_ISSUER_URI`.

## Как устроено

| Путь                                | Что делает                                                                  |
| ----------------------------------- | --------------------------------------------------------------------------- |
| `/oauth2/authorization/keycloak`    | старт логина, после входа редирект на `/`                                   |
| `/login/oauth2/code/keycloak`       | callback от Keycloak                                                        |
| `POST /logout`                      | закрывает сессию, отдаёт `202` и адрес выхода из Keycloak в `Location`      |
| `/api/bonds/**`                     | проксируется в invest-api на `/internal/rest/bonds/**`                      |
| `/actuator/health`                  | без авторизации                                                             |

- Без сессии `/api/**` отвечает `401` (UI сам уходит на логин), остальные страницы - редиректом на логин.
- CSRF: gateway ставит cookie `XSRF-TOKEN`, изменяющие запросы должны вернуть её значение в заголовке `X-XSRF-TOKEN`.
- Maintenance-эндпоинты invest-api наружу не проброшены.
- Redirect URI и post-logout redirect строятся от адреса, который видит браузер (`forward-headers-strategy: framework`),
  поэтому за Vite proxy логин возвращает на `:5173`. Новый адрес UI нужно добавить в redirect URIs клиента в realm.
