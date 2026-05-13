# plus-ms-auth

Microsserviço de autenticação do projeto **Plus**.

Expõe uma API REST em Spring Boot com JWT para login, refresh, logout e consulta do usuário autenticado. Persiste usuários em PostgreSQL e integra-se com front-ends via API REST.

---

## Tecnologias

- Java 17
- Spring Boot
- Spring Data JPA
- Spring Security
- JWT para access token
- PostgreSQL
- Flyway para migração de banco
- Documentação e ADR em `docs/README.md`

---

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/login` | Autentica com email e senha; retorna access token e refresh token |
| POST | `/auth/register` | Cria um novo usuário (ADMIN) |
| POST | `/auth/refresh` | Troca um refresh token válido por um novo access token |
| POST | `/auth/logout` | Encerra sessão de refresh token |
| GET | `/auth/me` | Retorna os dados do usuário autenticado (`Authorization: Bearer <token>`) |
| GET | `/users` | Lista todos os usuários (ADMIN) |
| DELETE | `/users/{id}` | Deleta usuário por id (ADMIN) |
| PATCH | `/users/{id}` | Atualiza usuário por id (ADMIN) |

Swagger UI disponível em `http://localhost:3001/swagger-ui.html`.

---

## Variáveis de ambiente

Copie `.env.example` e ajuste conforme necessário:

```bash
cp .env.example .env
```

| Variável | Padrão | Descrição |
|---|---|---|
| `PORT` | `3001` | Porta do servidor |
| `JWT_SECRET` | `my-secret-key` | Segredo para assinar os tokens |
| `DB_HOST` | `localhost` | Host do PostgreSQL |
| `DB_PORT` | `5432` | Porta do PostgreSQL |
| `DB_USER` | `postgres` | Usuário do banco |
| `DB_PASSWORD` | `postgres` | Senha do banco |
| `DB_NAME` | `postgres` | Nome do banco |
| `SEED_ADMIN_ENABLED` | `false` | Habilita seed de usuário admin |
| `SEED_ADMIN_USERNAME` | `admin` | Usuário admin seed |
| `SEED_ADMIN_EMAIL` | `admin@plus.local` | Email admin seed |
| `SEED_ADMIN_PASSWORD` | `admin123` | Senha admin seed |

> Observação: o arquivo `.env.example` do repositório contém variáveis adicionais de infração que não são diretamente referenciadas pelo código deste projeto.

## CI/CD

Este repositório possui pipeline configurado com GitHub Actions.

O pipeline executa automaticamente:

- testes unitários;
- build da aplicação;
- geração de release, quando aplicável.

Workflow principal:

```text
.github/workflows/ci.yml
```

O workflow usa o Maven Wrapper para executar os comandos:

- `./mvnw test`
- `./mvnw clean package -DskipTests`

Além do workflow principal, há um workflow de release acionado por push de tags `v*`:

```text
.github/workflows/release.yml
```

---

## Desenvolvimento local (sem Docker)

```bash
./mvnw spring-boot:run
```

> Para rodar isolado, é necessário ter o PostgreSQL disponível na porta configurada em `.env`.

---

## Executando com a stack completa

Este serviço pode ser executado com Docker Compose em `docker-compose.yml`.

O projeto também é orquestrado pelo `plus-infra`. Encontrado nesse repositório [README do plus-infra](https://github.com/pucrs-sweii-2026-1-30/plus-infra).
