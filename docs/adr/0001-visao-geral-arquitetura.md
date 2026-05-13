# ADR 0001 - Visão Geral da Arquitetura

## Status
Aceito

## Contexto
Este repositório é um microsserviço responsável por autenticação e gerenciamento de usuários em uma arquitetura de sistema distribuído.

O serviço foi implementado com Spring Boot, usando camadas típicas de `controller`, `service`, `repository` e `domain`.

## Decisão
- Utilizar Spring Boot como base do microsserviço.
- Separar as responsabilidades em controllers, services, repositories e domain.
- Usar Spring Data JPA para acesso a dados e Flyway para migrações de banco.
- Expor APIs REST para autenticação e gerenciamento de usuários.

## Consequências
- Desenvolvimento acelerado pela convenção do Spring Boot.
- Facilidade para testes unitários e integração com Spring Security.
- Migrações de banco versionadas e automatizadas pelo Flyway.
- Foco em deploy containerizado com Docker.

## Alternativas consideradas
- Não há evidência no código de uso de arquiteturas alternativas, como gRPC ou event-driven.
- A opção por Spring Boot é predominante e consistente com dependências do `pom.xml`.
- A confirmar: se haveria necessidade futura de uma camada de API Gateway ou serviço de descoberta.
