# Documentação do Microsserviço de Autenticação

Este repositório implementa um microsserviço de autenticação e gerenciamento de usuários usando Spring Boot.

## Visão Geral

- Backend em Java 17 com Spring Boot.
- Banco de dados PostgreSQL com migrações gerenciadas pelo Flyway.
- Autenticação baseada em JWT e refresh tokens.
- API REST exposta em `/auth` e `/users`.
- Documentação de API via Swagger/OpenAPI.
- Docker e Docker Compose para execução local.

## Estrutura da Documentação

- `docs/adr/0001-visao-geral-arquitetura.md`: Decisão de arquitetura geral.
- `docs/adr/0002-decisoes-de-api-e-integracao.md`: Decisões de API, autenticação, segurança, Docker e integração.
- `.github/workflows/ci.yml`: pipeline de CI para testes e build.
- `.github/workflows/release.yml`: pipeline de release acionado por tags `v*`.

## CI/CD

A integração contínua está configurada com GitHub Actions. O workflow principal é `.github/workflows/ci.yml`, que executa testes e build. O workflow `.github/workflows/release.yml` cria releases a partir de tags `v*`.

## Swagger / OpenAPI

A documentação interativa da API está disponível em `http://localhost:3001/swagger-ui.html` quando o serviço estiver em execução localmente. O endpoint OpenAPI JSON fica em `http://localhost:3001/v3/api-docs`.

## Como o front consome a API

O front-end deve consumir esta API por meio de requisições HTTP para os endpoints expostos. Os principais comportamentos são:

- `POST /auth/login`: recebe email e senha; retorna access token JWT e refresh token.
- `POST /auth/refresh`: recebe refresh token; retorna um novo access token.
- `GET /auth/me`: obtém informações do usuário autenticado com `Authorization: Bearer <token>`.
- Endpoints administrativos como `GET /users` e `DELETE /users/{id}` exigem role `ADMIN`.

O backend permite chamadas CORS de qualquer origem (`allowedOriginPatterns("*")`). Isso facilita a integração com aplicações front-end hospedadas em domínios diferentes.

> Nota: detalhes específicos de implementação de renovação de token no front não estão presentes no código e precisam ser definidos pela equipe de front-end.
