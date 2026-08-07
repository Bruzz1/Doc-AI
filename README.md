# Doc-AI

Doc-AI is a Spring Boot document assistant that combines invite-only authentication,
multi-tenant organizations, Apache Tika document parsing, PostgreSQL/pgvector vector
search, and an Ollama-backed RAG chat experience.

Administrators upload company documents and manage users. Authenticated users can ask
questions against the knowledge base, while administrators can also use the document
management screens to inspect and remove indexed files.

## Architecture

```text
Browser / API client
		|
		v
Spring Boot controllers and Thymeleaf UI
		|
		+--> JWT cookie authentication and role checks
		|
		+--> KnowledgeService -> Apache Tika -> chunking -> Ollama embeddings
		|                                      |
		|                                      v
		|                              PostgreSQL + pgvector
		|
		+--> ChatService -> organization-filtered similarity search
									  |
									  v
							  Ollama chat model -> answer
```

Important source locations:

| Area | Main files |
|---|---|
| Application entry point | `src/main/java/com/bruce/docai/DocaiApplication.java` |
| Web controllers | `controller/`, `security/controller/` |
| RAG and parsing | `service/KnowledgeService.java`, `service/ChatService.java`, `service/TikaDocumentParserServiceImpl.java` |
| Security | `security/config/SecurityConfig.java`, `security/filter/`, `security/service/` |
| Persistence | `model/`, `repository/`, `src/main/resources/schema.sql` |
| UI | `src/main/resources/templates/`, `src/main/resources/static/style.css` |

## Requirements

- Java 17
- Maven
- PostgreSQL with the `vector`, `hstore`, and `uuid-ossp` extensions
- Ollama with the following models:
  - `qwen2.5:3b` for chat generation
  - `nomic-embed-text` for embeddings

The included `compose.yaml` starts a pgvector PostgreSQL container. Ollama is not
currently enabled in that file, so run Ollama separately.

## Local setup

### 1. Start PostgreSQL/pgvector

```bash
docker compose up -d pgvector
```

The default application configuration expects:

```text
Database: ics
User:     postgres
Password: postgres
Host:     localhost
Port:     5432
```

If Docker assigns a different host port, update `spring.datasource.url` in
`src/main/resources/application.yaml` or add an explicit host mapping to
`compose.yaml`.

### 2. Start Ollama and download models

```bash
ollama serve
ollama pull qwen2.5:3b
ollama pull nomic-embed-text
```

The application uses `http://localhost:11434/` by default.

### 3. Start the application

```bash
mvn spring-boot:run
```

The server listens on `http://localhost:8081`.

On first startup, `DataLoader` checks the vector dimension and loads
`src/main/resources/RJBRUCE_CV.pdf` into `default-org` if `vector_store` is empty.

## First admin account

Admin bootstrap is disabled by default. Enable it temporarily to create the first
administrator:

```bash
export APP_SECURITY_BOOTSTRAP_ADMIN_ENABLED=true
export APP_SECURITY_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
export APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD='YourSecurePassword123!'
export APP_SECURITY_BOOTSTRAP_ADMIN_NAME='System Admin'
export APP_SECURITY_BOOTSTRAP_ORG_ID=default-org
mvn spring-boot:run
```

Sign in at `http://localhost:8081/login`. A bootstrap account is created with
`mustChangePassword=true`, so the first login redirects to the password-change page.
After changing the password, disable bootstrap and restart the application:

```bash
export APP_SECURITY_BOOTSTRAP_ADMIN_ENABLED=false
```

## Authentication and authorization

Authentication is invite-only and uses short-lived JWT access tokens plus rotating
refresh tokens.

1. An admin creates an invite.
2. The invite stores only a SHA-256 hash of its random token.
3. The recipient accepts the invite and creates a password.
4. The user is assigned the invite's organization and role.
5. Access and refresh tokens are issued as HttpOnly cookies.

The default roles are `ADMIN` and `USER`. Admin endpoints use Spring Security method
authorization with `hasRole('ADMIN')`.

The default cookie behavior is:

- `HttpOnly`
- `Secure=true`
- `SameSite=Lax`
- Path `/`

For local HTTP development, use:

```bash
export APP_SECURITY_COOKIE_SECURE=false
```

## Invite API

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `POST` | `/auth/invites` | Admin | Create an invite |
| `GET` | `/auth/invites` | Admin | List invites |
| `POST` | `/auth/invites/{id}/regenerate` | Admin | Replace an unused invite |
| `DELETE` | `/auth/invites/{id}` | Admin | Cancel an unused invite |
| `POST` | `/auth/accept-invite` | Public | Consume an invite and create an account |

The admin UI at `/admin/invites` creates and copies links in the form:

```text
/accept-invite?token=<invite-token>
```

Invite tokens expire after seven days by default.

## Session API

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/auth/login` | Authenticate a user |
| `POST` | `/auth/refresh` | Rotate the refresh token and issue a new access token |
| `POST` | `/auth/logout` | Revoke the refresh token and clear cookies |
| `GET` | `/auth/me` | Return the current user's profile and organization |
| `POST` | `/auth/change-password` | Change the current password and revoke old sessions |

Access tokens last 15 minutes by default. Refresh tokens last 14 days.

## Document management

The organization-aware document workflow is:

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `GET` | `/admin/documents` | Admin | List documents for the admin's organization |
| `POST` | `/admin/documents` | Admin | Upload and index a document |
| `DELETE` | `/admin/documents/{id}` | Admin | Delete metadata and its vector chunks |
| `GET` | `/admin/documents/page` | Admin | Open the document management UI |

Supported files are PDF, DOC, DOCX, and TXT. Uploads are limited to 20 MB by
`application.yaml`.

The upload pipeline is:

1. Validate the file extension and detected MIME type.
2. Calculate a SHA-256 checksum and reject duplicates within the organization.
3. Extract text with Apache Tika.
4. Normalize whitespace and line endings.
5. Split the text into chunks.
6. Generate embeddings with Ollama.
7. Store chunks, metadata, and embeddings in pgvector.
8. Save the chunk count in `rag_documents`.

If indexing fails, the service removes both the document record and any vectors
created for that upload.


## Chat and RAG API

| Method | Endpoint | Behavior |
|---|---|---|
| `GET` | `/faqs?question=...` | Organization-filtered RAG question answering |
| `GET` | `/chat?message=...` | Direct Ollama chat without document retrieval |

The main `/app` UI defaults to RAG mode and lets the user switch between RAG and
direct chat.

### RAG behavior

`/faqs` performs a cosine-similarity search in pgvector using the authenticated user's
`organizationId`. It retrieves the best chunks, retries with a broader search when
too few results are found, removes duplicates, and limits the prompt context to
12,000 characters by default.

The model is instructed to answer only from the retrieved context. If there is not
enough information, the service returns:

```text
I don't have enough information to answer that question.
```

The direct `/chat` endpoint does not use the vector store and is not document-grounded.

## UI routes

| Route | Access | Purpose |
|---|---|---|
| `/` | Public | Redirects to `/login` |
| `/login` | Public | Login form |
| `/accept-invite` | Public | Account creation from an invite |
| `/app` | Authenticated | Main chat and RAG workspace |
| `/change-password` | Authenticated | Required password-change screen |
| `/admin/invites` | Admin | Invite dashboard |
| `/admin/documents/page` | Admin | Document dashboard |

The browser automatically attempts `/auth/refresh` after an API request receives a
`401` response. If refresh fails, it redirects to `/login`.

## Configuration

The main settings are in `src/main/resources/application.yaml` and can be overridden
with environment variables or Spring properties.

| Setting | Default | Purpose |
|---|---:|---|
| `server.port` | `8081` | HTTP port |
| `spring.datasource.url` | PostgreSQL on `localhost:5432/ics` | Database connection |
| `spring.ai.ollama.base-url` | `http://localhost:11434/` | Ollama URL |
| `spring.ai.ollama.chat.options.model` | `qwen2.5:3b` | Chat model |
| `spring.ai.ollama.embedding.options.model` | `nomic-embed-text` | Embedding model |
| `spring.ai.vectorstore.pgvector.dimensions` | `768` | Embedding dimension |
| `app.rag.top-k` | `6` | Primary retrieval count |
| `app.rag.fallback-top-k` | `10` | Fallback retrieval count |
| `app.rag.similarity-threshold` | `0.35` | Primary similarity threshold |
| `app.rag.max-context-chars` | `12000` | Maximum prompt context |
| `app.rag.chunk-size` | `500` | Chunking target size |
| `app.security.jwt.secret` | Development placeholder | JWT signing secret |
| `app.security.cookie.secure` | `true` | Require HTTPS for cookies |

For production, set a long random JWT secret using:

```bash
export APP_SECURITY_JWT_SECRET='replace-with-a-long-random-secret'
```

Do not use the development JWT secret in a deployed environment.

## Tests

Run the automated tests with:

```bash
mvn test
```

The test suite covers JWT validation, admin bootstrap, document controller responses,
Tika parsing of TXT/PDF/DOCX files, unsupported file rejection, RAG fallback and
deduplication, context truncation, and grounded answers.

The full Spring context test is disabled because it requires external PostgreSQL/
pgvector and runtime AI services.

## Current limitations

- Ollama must be installed and running separately; it is not enabled in `compose.yaml`.
- The application is configured for development schema updates with
  `spring.jpa.hibernate.ddl-auto=update`.
- The seed PDF is loaded into `default-org` only when the vector store is empty.
- `/chat` is direct model chat and does not apply organization-filtered RAG retrieval.
- `/webhook/whatsapp` exists as a placeholder, but no WhatsApp webhook endpoint is
  implemented yet.
