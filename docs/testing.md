# Тестирование invest-gateway

```bash
mvn verify          # checkstyle + тесты
```

Keycloak и invest-api для тестов не нужны.

## Устройство

`app/src/test/java/ru/invest/gateway/SecurityConfigurationTest` - `@SpringBootTest` с заглушкой Keycloak
(`KeycloakStubConfiguration`: регистрация клиента без похода за OIDC discovery), конфиг -
`app/src/test/resources/application.yml`.

| Тест                                       | Что проверяет                                         |
| ------------------------------------------ | ----------------------------------------------------- |
| `apiWithoutSessionReturnsUnauthorized`     | `/api/**` без сессии - `401`, а не редирект           |
| `pageWithoutSessionRedirectsToLogin`       | страницы без сессии - редирект на логин               |
| `healthIsPublic`                           | `/actuator/health` без авторизации                    |
| `postWithoutCsrfTokenIsForbidden`          | изменяющий запрос без `X-XSRF-TOKEN` - `403`          |
| `logoutReturnsLocationInsteadOfRedirect`   | logout отвечает `202` с адресом выхода в `Location`   |
| `authenticatedApiRequestIsRoutedToInvestApi` | `/api/bonds/**` с сессией уходит в invest-api на `/internal/rest/bonds/**` |
| `alertsAreRoutedToInvestApi`, `alertsWithoutSessionReturnUnauthorized` | `/api/alerts/**` с сессией уходит на `/internal/rest/alerts/**`, без сессии - `401` |
| `portfolioIsRoutedToInvestApi`, `portfolioWithoutSessionReturnsUnauthorized` | `/api/portfolio/**` с сессией уходит на `/internal/rest/portfolio/**`, без сессии - `401` |
| `UserIdHeaderFilterTest`                   | `X-User-Id` = `sub` OIDC-пользователя; поддельный заголовок из браузера удаляется, у анонима заголовка нет |

Новое правило в `SecurityConfiguration` или новый маршрут - новый тест здесь.

Checkstyle (`configuration/checkstyle/checkstyle.xml`) запускается в `validate`; pre-commit hook ставит
`git-build-hook-maven-plugin`.
