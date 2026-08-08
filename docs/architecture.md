# Doc-AI — High-Level Architecture

**Version:** 1.0.0
**Last updated:** 2026-08-08
**App version:** 0.0.1-SNAPSHOT (see `pom.xml`)

Doc-AI is a multi-tenant RAG (Retrieval-Augmented Generation) application built on
Spring Boot 3.5 + Spring AI. Admins upload documents that are parsed, chunked, and
embedded into a pgvector store; users ask questions that are answered by retrieving
relevant chunks and passing them to a local Ollama LLM.

## Stack

- Spring Boot 3.5.4 · Java 17
- Spring AI 1.0.0 (Ollama chat + embeddings, pgvector vector store)
- PostgreSQL + pgvector (relational data + 768-dim embeddings, HNSW / cosine)
- Spring Security + JWT (cookie-based) · Flyway migrations
- Apache Tika + POI (document parsing) · Thymeleaf (UI)
- Micrometer / Prometheus (metrics) · Spring Boot Actuator

## Component Diagram

```mermaid
flowchart TB
    subgraph Clients
        Browser["Browser (Thymeleaf UI)"]
        WhatsApp["WhatsApp webhook (TODO)"]
    end

    subgraph App["Spring Boot App (docai) :8081"]
        subgraph Security["Security Layer (Spring Security + JWT)"]
            Filters["JwtAuthFilter → MustChangePasswordFilter"]
            SecSvc["JwtService · RefreshTokenService · AuthCookieService<br/>InviteService · TokenHashingService · AdminBootstrapRunner"]
        end

        subgraph Controllers["Controllers (web layer)"]
            Ui["UiController<br/>(Thymeleaf pages)"]
            Auth["AuthController<br/>/auth/login /refresh /invites …"]
            AdminDoc["AdminDocumentController<br/>/admin/documents (ADMIN)"]
            Chat["ChatController<br/>/chat /faqs"]
            Wa["WhatsappController<br/>(webhook, TODO)"]
        end

        subgraph Services["Services (business layer)"]
            Tenant["TenantService · TenantContext<br/>AuditService · InviteService"]
            Knowledge["KnowledgeService<br/>(upload, dedup, list, delete)"]
            Ingest["DocumentIngestionService<br/>(async thread pool, status tracking)"]
            Parser["TikaDocumentParserService<br/>(Tika+POI parse → TokenTextSplitter → chunks)"]
            ChatSvc["ChatService<br/>(RAG: retrieve → prompt → LLM)"]
        end

        subgraph Data["Data / AI Access"]
            Repos["Spring Data repos (JDBC/JPA)<br/>User · Organization · RagDocument<br/>RefreshToken · InviteToken"]
            Vector["Spring AI VectorStore<br/>(pgvector, HNSW, cosine)"]
            Flyway["Flyway migrations<br/>V1 baseline · V2 RLS · V3 status"]
            Metrics["DocumentMetrics → Actuator / Prometheus"]
        end
    end

    subgraph External
        Postgres[("PostgreSQL + pgvector (DB: ics)<br/>relational tables + 768-dim vectors<br/>Row-Level Security per tenant")]
        Ollama["Ollama :11434<br/>chat: qwen2.5:3b<br/>embed: nomic-embed-text"]
    end

    Browser --> Controllers
    WhatsApp --> Wa
    Controllers --> Security

    Auth --> Tenant
    AdminDoc --> Knowledge
    Chat --> ChatSvc
    Wa --> ChatSvc

    Knowledge -->|async| Ingest
    Ingest --> Parser
    Parser -->|embed| Vector
    ChatSvc -->|embed + search| Vector

    Tenant --> Repos
    Knowledge --> Repos
    Repos --> Postgres
    Vector --> Postgres
    Parser -->|embed| Ollama
    ChatSvc -->|chat + embed| Ollama
```

## Key Flows

1. **Auth** — JWT stored in cookies. Invite-based onboarding, hashed refresh tokens,
   forced password change, admin bootstrap, and role-based access (`ADMIN`).
2. **Ingestion (admin)** — upload → `KnowledgeService` (SHA-based dedup) → async
   `DocumentIngestionService` (bounded thread pool; status column drives UI polling)
   → Tika/POI parse → chunk → embed → pgvector.
3. **Query (RAG)** — `/chat` & `/faqs` → `ChatService` retrieves top-K similar chunks
   (cosine, similarity threshold 0.35) from pgvector → builds context prompt →
   Ollama LLM → answer.
4. **Multi-tenancy** — `TenantContext` + `TenantService` + Postgres Row-Level Security
   scope all data per `organizationId`.
