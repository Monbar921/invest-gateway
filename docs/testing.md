# Тестирование invest-gateway

```bash
mvn verify          # checkstyle + тесты
```

Keycloak и invest-api для тестов не нужны.

## Устройство

`app/src/test/java/ru/invest/gateway/SecurityConfigurationTest` - `@SpringBootTest` с заглушкой Keycloak
(`KeycloakStubConfiguration`: регистрация клиента без похода за OIDC discovery, подключается `@Import`), конфиг -
`app/src/test/resources/application.yml` (он в `.gitignore` - в репозитории его нет). Лимит анонимных запросов в
`SecurityConfigurationTest` выключен (`requests-per-minute=0` в `@SpringBootTest`): все тесты шлют запросы с одного
адреса; лимит проверяет `AnonymousRateLimitTest` со своим значением.

| Тест                                       | Что проверяет                                         |
| ------------------------------------------ | ----------------------------------------------------- |
| `pageWithoutSessionRedirectsToLogin`       | страницы без сессии - редирект на логин               |
| `healthIsPublic`                           | `/actuator/health` без авторизации                    |
| `postWithoutCsrfTokenIsForbidden`          | изменяющий запрос без `X-XSRF-TOKEN` - `403`          |
| `logoutReturnsLocationInsteadOfRedirect`   | logout отвечает `202` с адресом выхода в `Location`   |
| `authenticatedApiRequestIsRoutedToInvestApi` | `/api/bonds/**` с сессией уходит в invest-api на `/internal/rest/bonds/**` |
| `alertsAreRoutedToInvestApi`, `alertsWithoutSessionReturnUnauthorized` | `/api/alerts/**` с сессией уходит на `/internal/rest/alerts/**`, без сессии - `401` |
| `portfolioIsRoutedToInvestApi`, `portfolioWithoutSessionReturnsUnauthorized` | `/api/portfolio/**` с сессией уходит на `/internal/rest/portfolio/**`, без сессии - `401` |
| `accountIsRoutedToInvestApi`, `accountWithoutSessionReturnsUnauthorized` | `/api/account/**` (тариф) с сессией уходит на `/internal/rest/account/**`, без сессии - `401` |
| `calendarFeedWithoutSessionIsRoutedToInvestApi`, `calendarSettingsStillRequireLogin` | `GET /api/calendar/<токен>.ics` без сессии уходит на `/internal/rest/calendar/<токен>.ics`; настройки ссылки (`/api/portfolio/calendar/link`), `POST` и вложенный путь под `/api/calendar` без сессии - `401` |
| `bondReadWithoutSessionIsRoutedToInvestApi`, `bondPageAndCalculationWithoutSessionAreRoutedToInvestApi` | публичный режим: чтение облигаций и POST страницы и расчёта без сессии уходят в invest-api |
| `brokerClickWithoutSessionIsRoutedToInvestApi` | публичный режим: учёт перехода к брокеру-партнёру без сессии уходит в invest-api |
| `brokerClickWithoutCsrfTokenIsForbidden`, `otherBrokerLinkPostsStillRequireLogin` | клик гостя без `X-XSRF-TOKEN` - `403`; другие POST под `broker-links` (`/broker-links`, `/broker-links/{id}`, лишний сегмент после `click`) без сессии - `401` |
| `anonymousPostWithoutCsrfTokenIsForbidden`, `csrfCookieIsIssuedWithoutSession` | анонимный POST без `X-XSRF-TOKEN` - `403`; cookie `XSRF-TOKEN` выдаётся без сессии |
| `errorDispatchOfGuestKeepsOriginalStatus`  | error dispatch у гостя отдаёт исходный статус (`403`), а не `401` |
| `otherBondPostsStillRequireLogin`          | другие POST под `/api/bonds` без сессии - `401`        |
| `sessionOfGuest`, `sessionOfLoggedInUser`  | `/api/session` у гостя и вошедшего                    |
| `loginRemembersReturnPath`, `loginIgnoresForeignReturnAddress` | `returnTo` сохраняется в сессии, чужой адрес (`//host`) - нет |
| `LoginReturnTest`                          | какие `returnTo` допустимы; после входа - редирект на него один раз, без него - на `/` |
| `AnonymousRateLimiterTest`                 | token bucket: всплеск до лимита, пополнение, `Retry-After`, `0` - без лимита, память при множестве IP |
| `AnonymousRateLimitTest`                   | `429` сверх лимита по IP, у другого IP свой запас, вошедший и не-`/api` - без лимита |
| `PublicAccessDisabledTest`                 | `public-access.enabled=false`: облигации и клик по ссылке брокера без сессии - `401`, `/api/session` и календарь `.ics` по ссылке открыты |
| `SeoPagesTest`                             | SEO-страницы с HTTP-заглушкой invest-api и UI: метаданные и содержимое облигации, экранирование, `404` с `noindex`, `503` при ошибке invest-api, без новостей, последняя оболочка при недоступном UI, эмитент, подборка, список (canonical без query), вошедший, `sitemap.xml`, `robots.txt` (закрыты и `/api/calendar`, `/plan`, `/api/account`), другие страницы - под логином; `HEAD` без сессии, canonical и sitemap по `X-Forwarded-*`, тикер с пробелом (запрос в invest-api и canonical), эмитент без названий и выпусков, `503` эмитента, пустая подборка, неизвестная подборка - `404`, sitemap с `null`-списками, sitemap - корректный XML с закодированными адресами, ошибка invest-api на sitemap - `503` с `Retry-After` |
| `HtmlPageRendererTest`                     | вставка в оболочку: описание UI в несколько строк заменяется без дубля, оболочка без `#root` и с непустым `#root` - один `#root`, `$` и `\` в тексте, `404` без canonical и og:url, экранирование title и description |
| `SeoPageBuilderTest`                       | облигация без цены, доходности и имени, тикер в адресах кодируется, оферта и купон, эмитент без названия, новости без заголовка и без ссылки, ссылка новости только `http(s)`, эмитент без названий, пустая подборка, description не длиннее 300 символов, список без подборок |
| `BaseUrlResolverTest`                      | адрес сайта: `public-url` с «/» и пробелами, пустой - из запроса без query, порт по умолчанию не пишется |
| `UiTemplateProviderTest`                   | оболочка UI: кэш на ttl, последняя полученная при ошибке, минимальная без единой, ответ без `#root` не принимается |
| `AnonymousRateLimitTest.seoPagesShareTheLimit`, `PublicAccessDisabledTest.seoPagesRequireLogin` | SEO-страницы в общем лимите; без публичного режима - под логином |
| `UserIdHeaderFilterTest`                   | `X-User-Id` = `sub` OIDC-пользователя; поддельный заголовок из браузера удаляется, у анонима заголовка нет |

Новое правило в `SecurityConfiguration` или новый маршрут - новый тест здесь.

Checkstyle (`configuration/checkstyle/checkstyle.xml`) запускается в `validate`; pre-commit hook ставит
`git-build-hook-maven-plugin`.
