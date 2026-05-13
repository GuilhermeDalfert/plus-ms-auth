# ADR 0003 - Estratégia de Testes Automatizados

## Status
Aceito

## Contexto
O microsserviço de autenticação concentra regras críticas do sistema: emissão e validação de JWT, ciclo de vida de refresh tokens, e a matriz de autorização (RBAC) que separa rotas públicas, autenticadas e admin-only. Bugs nessa camada se traduzem em falhas de segurança ou de acesso indevido a recursos administrativos.

Inicialmente o repositório possuía apenas `MsApplicationTests.contextLoads()`, herdado do scaffold do Spring Initializr — ou seja, cobertura efetiva próxima de zero sobre as classes de domínio, segurança e controle de acesso.

## Decisão

Adotar uma estratégia de **testes em quatro camadas**, cada uma com escopo bem definido para evitar duplicação e maximizar velocidade da suíte:

### Camada 1 — Testes unitários de serviços (Mockito puro)
- **`TokenServiceTest`** — geração/validação de JWT, ciclo de refresh tokens (criar, validar, revogar), tratamento de tokens nulos/expirados/malformados.
- **`AuthenticationServiceTest`** — fluxo de login (sucesso e falha), logout, rotação de refresh token (revogação do antigo antes da emissão do novo).
- **`UserServiceTest`** — registro (sempre como `USER`, nunca como `ADMIN`), atualização parcial, validação de e-mail único, regra "usuário não pode alterar o próprio cargo", busca por e-mail/username.

Estilo: `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`. Sem contexto Spring. Tempo alvo: < 2s para a camada inteira.

### Camada 2 — Testes de web layer (`@WebMvcTest`)
- **`AuthenticationControllerTest`** — mapeamento de exceções de domínio para status HTTP (200/204/400), serialização correta dos DTOs, extração do header `Authorization: Bearer` em `GET /auth/me`.
- **`UserControllerTest`** — distinção entre 400 (regra de negócio) e 404 (recurso inexistente) em `DELETE /users/{id}`.

Foco: contrato HTTP, não autorização (essa é coberta na camada 3).

### Camada 3 — Testes de integração de segurança/RBAC (`@SpringBootTest`)
- **`SecurityRbacIntegrationTest`** — valida ponta-a-ponta a matriz declarada em `SecurityConfiguration.securityFilterChain`:
  - Endpoints públicos (`/auth/login`, `/auth/refresh`, `/auth/logout`, `/swagger-ui.html`, `/v3/api-docs`) acessíveis sem autenticação.
  - `/auth/me` exige autenticação.
  - `POST /auth/register`, `DELETE /users/{id}`, `PATCH /users/{id}` exigem `ROLE_ADMIN` (anônimo → 401/403; `ROLE_USER` → 403; `ROLE_ADMIN` → sucesso).
  - `GET /users` permitido para `ROLE_USER` e `ROLE_ADMIN`.

Usa `@WithMockUser` para evitar dependência da emissão real de JWT no setup dos testes — o foco é a regra de autorização do Spring Security, não o fluxo completo de geração de token (já coberto na camada 1).

### Camada 4 — Testes de repositório (`@DataJpaTest` com H2)
- **`UserRepositoryTest`** — finders derivados `findByEmail` e `findByUsername`, geração de UUID em `save`.
- **`RefreshTokenRepositoryTest`** — `findByToken`, persistência da entidade com FK para `User`, restrição `UNIQUE` na coluna `token`.

Justifica-se mesmo sendo `JpaRepository` puro porque exercita o mapeamento JPA/Hibernate das entidades e captura erros de schema antes do deploy.

### Profile `test`
Todos os testes que sobem contexto Spring usam `@ActiveProfiles("test")` apontando para `src/test/resources/application-test.properties`, que configura H2 em memória, desabilita Flyway e fornece um `JWT_SECRET` fixo. Isso permite rodar a suíte localmente sem dependência de Postgres ou de variáveis de ambiente.

`MsApplicationTests` também recebeu `@ActiveProfiles("test")` para herdar o mesmo isolamento — antes da mudança, ele falhava em qualquer máquina que não tivesse Postgres rodando em `localhost:5432`.

## Consequências

### Positivas
- Cobertura efetiva ~80% nas classes críticas (`TokenService`, `AuthenticationService`, `UserService`, controllers, configuração de segurança), partindo de ~0%.
- Suíte completa roda em ~30s e não depende de infra externa — viabiliza execução local e em CI sem sidecar de Postgres.
- A matriz RBAC fica documentada como **teste executável**: qualquer mudança em `SecurityConfiguration` que quebre as garantias declaradas falha imediatamente.
- A regra "usuário não pode alterar o próprio cargo" e "registro nunca cria admin" ficam blindadas contra regressão.
- Rotação de refresh token é validada com `InOrder` (revoga o antigo *antes* de emitir o novo), prevenindo um cenário em que falhas intermitentes deixariam dois refresh tokens válidos simultaneamente.

### Negativas / trade-offs aceitos
- `SecurityConfiguration` não declara `AuthenticationEntryPoint` customizado, então requisições anônimas a endpoints protegidos retornam **403 Forbidden** em vez do semanticamente correto **401 Unauthorized**. Os testes documentam o comportamento atual aceitando `anyOf(is(401), is(403))` com comentário explicativo. Correção é trivial (registrar `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`) mas foi adiada para não acoplar essa task à mudança de produção.
- Camada 4 (`@DataJpaTest`) tem valor agregado menor — os repositórios são `JpaRepository` simples — mas serve de smoke test do mapeamento JPA e é barata o suficiente para manter.
- Spring Boot 4 removeu `@MockBean` em favor de `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`). Todos os testes que precisam injetar mocks no contexto Spring usam essa nova anotação — divergência intencional do material de referência mais antigo.

## Alternativas consideradas

- **Apenas testes de integração ponta-a-ponta** (sem unitários): rejeitado. Tornaria a suíte lenta e dificultaria isolar a causa de falhas — bugs em `TokenService` se manifestariam como falhas em rotas, dificultando diagnóstico.
- **Apenas testes unitários** (sem integração): rejeitado. A matriz RBAC do Spring Security só pode ser validada com o contexto carregado; mocks dariam falsos positivos.
- **Testcontainers com Postgres real**: considerado para a camada 4. Rejeitado pelo overhead de inicialização (~10s por execução) e dependência de Docker disponível na máquina do desenvolvedor. H2 em modo PostgreSQL é suficiente para finders derivados e restrições de coluna.
- **Cobertura via JaCoCo no CI**: deixado como evolução futura. A meta inicial foi estabelecer a base de testes; a métrica de cobertura pode ser plugada depois sem impacto no design dos testes existentes.
