# Документация invest-gateway

| Файл                               | Что                                                             |
| ---------------------------------- | --------------------------------------------------------------- |
| [architecture.md](architecture.md) | BFF, OAuth2-логин в Keycloak, маршруты, CSRF, logout            |
| [testing.md](testing.md)           | тесты безопасности и маршрутизации                              |
| [deployment.md](deployment.md)     | локальный запуск, конфигурация, стенд в k3d                     |

## Фичи

Описаны в invest-api, gateway - их часть:

- [INVEST-001-public-mode](../../invest-api/docs/features/INVEST-001-public-mode/) - чтение облигаций без входа, `/api/session`, возврат после входа, лимит анонимных запросов

Связанные проекты: [invest-api](../../invest-api/docs/), [invest-api-ui](../../invest-api-ui/docs/),
[invest-deploy](../../invest-deploy/docs/).
