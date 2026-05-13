# ADR 0002 - Decisões de API, Autenticação, Segurança e Integração

## Status
Aceito

## Contexto
O microsserviço fornece endpoints de autenticação e gerenciamento de usuários. Ele deve ser seguro, integrado com front-end e apto a rodar em containers.

## Decisão
- API REST exposta em `/auth` e `/users`.
- Rotas principais:
  - `POST /auth/login`
  - `POST /auth/register`
  - `POST /auth/refresh`
  - `POST /auth/logout`
  - `GET /auth/me`
  - `GET /users`
  - `DELETE /users/{id}`
  - `PATCH /users/{id}`
- Uso de JWT para access tokens com expiração curta (15 minutos).
- Uso de refresh tokens persistidos em banco para renovação de sesssões por 7 dias.
- Autorização baseada em roles, conforme `SecurityConfiguration.java`:
  - `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout` — públicos.
  - `POST /auth/register` — restrito a `ADMIN`.
  - `GET /auth/me` — qualquer usuário autenticado.
  - `GET /users` — qualquer usuário autenticado (`hasAnyRole("ADMIN", "USER")`).
  - `PATCH /users/**` e `DELETE /users/**` — restritos a `ADMIN`.
- CORS configurado para permitir qualquer origem e qualquer header (`allowedOriginPatterns("*")`, `allowedHeaders("*")`), com `allowCredentials(true)`. Métodos liberados são fixos: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- Swagger UI disponível em `/swagger-ui.html` e OpenAPI JSON em `/v3/api-docs`.
- CSRF desabilitado devido ao modelo stateless de autenticação.
- Containerização com Docker e Docker Compose para PostgreSQL e serviço Java.
- Configuração via variáveis de ambiente no `application.properties`.

## Consequências
- A API se torna apta a ser consumida por front-ends externos graças ao CORS aberto.
- Segurança baseada em tokens facilita escalabilidade stateless.
- A dependência do `JWT_SECRET` e do banco deve ser gerenciada por variáveis de ambiente em produção.
- O serviço roda localmente com Docker Compose mapeando a porta `3001`.
- Sem garantias de proteção adicional contra ataques de token replay além da revogação de refresh tokens.

## Alternativas consideradas
- Não há código indicando uso de OAuth2 ou OpenID Connect; a implementação atual usa JWT customizado.
- Não há implementação de refresh token em cookies; a solução usa corpo de requisição e storage no banco.
- **Logout**: implementado em `AuthenticationController.logout()` recebendo o refresh token no body e delegando a revogação para `AuthenticationService.logout()`. Sem blacklist de **access tokens** — eles permanecem válidos até expirar (15 min). A mitigação de roubo se apoia na curta janela de expiração + revogação do refresh token.
