# Архитектура invest-gateway

Единая точка входа для [invest-api-ui](../../invest-api-ui/) и [invest-api](../../invest-api/).
Spring Cloud Gateway (Server WebMVC), Spring Boot 4, Java 25.

```
браузер ──▶ UI (Vite :5173 в dev / ingress на стенде) ──▶ invest-gateway ──▶ invest-api
                                                              │
                                                              └── OIDC ──▶ Keycloak (realm invest)
```

## BFF

Gateway работает как Backend-for-Frontend: сам проходит OAuth2-логин в Keycloak (authorization code + PKCE)
и хранит токены в HTTP-сессии. Браузер получает только session cookie, invest-api токенов не видит.

Почему так:

- токены не попадают в браузер (нет XSS-утечки access token);
- invest-api остаётся без Spring Security: он доверяет gateway и недоступен снаружи (слушает localhost / ClusterIP).

Отсюда ограничение: UI и gateway должны быть на одном origin, иначе cookie сессии не дойдут. В dev это делает
Vite proxy, на стенде - ingress.

## Маршруты

| Путь                             | Что                                                                       |
| -------------------------------- | ------------------------------------------------------------------------- |
| `/oauth2/authorization/keycloak` | старт логина, после входа - редирект на сохранённую страницу или `/`      |
| `/login/oauth2/code/keycloak`    | callback от Keycloak                                                      |
| `POST /logout`                   | закрывает сессию, отвечает `202` и адресом выхода из Keycloak в `Location` |
| `/api/bonds/**`                  | проксируется в invest-api на `/internal/rest/bonds/**` (`RewritePath`)    |
| `/api/alerts/**`                 | то же для уведомлений: `/internal/rest/alerts/**` (подключение Telegram)  |
| `/actuator/health`               | без авторизации                                                           |

Наружу открыты облигации и уведомления: maintenance-эндпоинты invest-api (`/internal/rest/maintenance/**`) через gateway
недоступны.

## Безопасность (`feature/authorization-service`)

`SecurityConfiguration`:

- всё, кроме `/actuator/health`, требует аутентификации;
- без сессии `/api/**` отвечает `401` (fetch из UI должен получить статус, а не HTML логина Keycloak),
  остальные страницы - редиректом на логин;
- CSRF в режиме SPA: gateway ставит cookie `XSRF-TOKEN`, изменяющие запросы возвращают её значение в заголовке
  `X-XSRF-TOKEN`;
- logout - OIDC RP-initiated: Keycloak тоже закрывает сессию и возвращает на `{baseUrl}/`. UI выходит через fetch,
  а fetch не может пройти редирект на другой origin, поэтому адрес выхода отдаётся в `Location` с кодом `202`,
  браузер переходит туда сам.

`UserIdHeaderFilter` (зарегистрирован сразу после цепочки Spring Security) передаёт пользователя в invest-api
заголовком `X-User-Id` - `sub` из OIDC-сессии Keycloak. Заголовок `X-User-Id` из браузера удаляется всегда, иначе его
можно было бы подделать; без входа заголовка нет. invest-api своей авторизации не имеет и доверяет этому заголовку,
поэтому сам он доступен только через gateway.

`LoggingAuthenticationSuccessHandler` / `LoggingAuthenticationFailureHandler` пишут в лог успешные и неудачные входы
(пользователь, IP, причина).

## Адреса за прокси

`server.forward-headers-strategy: framework`: redirect URI и post-logout redirect строятся от адреса, который видит
браузер (по `X-Forwarded-*`), а не от адреса gateway. За Vite proxy логин возвращает на `:5173`, на стенде - на адрес
ingress. Каждый новый адрес UI нужно добавить в redirect URIs клиента `invest-gateway` в Keycloak.

## Модули

| Модуль                           | Что                                              |
| -------------------------------- | ------------------------------------------------ |
| `app`                            | `InvestGatewayApplication`, тесты                |
| `feature/authorization-service`  | `SecurityConfiguration`, обработчики логина, `UserIdHeaderFilter` |

Маршруты описаны в `config/application.yml` (`spring.cloud.gateway.server.webmvc.routes`), кода для них нет.
