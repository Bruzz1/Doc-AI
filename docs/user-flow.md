# Doc-AI User Flow

This diagram shows how users interact with Doc-AI today, from invitation through
authentication, document management, and RAG chat.

## User journey (activity flow)

```mermaid
flowchart TD
    Start([User visits site]) --> Root["/ redirects to /login"]

    subgraph Onboarding [Invite-only onboarding]
        AdminInvite["Admin creates invite<br/>POST /auth/invites"]
        AdminInvite --> Link["Admin copies link<br/>/accept-invite?token=..."]
        Link --> Accept["Recipient opens /accept-invite<br/>sets password<br/>POST /auth/accept-invite"]
        Accept --> Assigned["User assigned org + role<br/>(ADMIN or USER)"]
    end

    Root --> Login["/login form"]
    Assigned --> Login
    Login --> DoLogin["POST /auth/login"]
    DoLogin -->|invalid| Login
    DoLogin -->|valid| Cookies["JWT access + refresh<br/>set as HttpOnly cookies"]

    Cookies --> MustChange{mustChangePassword?}
    MustChange -->|yes| ChangePw["/change-password<br/>POST /auth/change-password"]
    ChangePw --> App
    MustChange -->|no| App["/app workspace"]

    App --> RoleCheck{Role?}

    subgraph UserActions [Authenticated user]
        RAG["Ask question (RAG mode)<br/>GET /faqs?question=..."]
        Chat["Direct chat mode<br/>GET /chat?message=..."]
        RAG --> Answer["Org-filtered answer<br/>from pgvector context"]
        Chat --> DirectAns["Ungrounded model answer"]
    end

    subgraph AdminActions [Admin only]
        Invites["/admin/invites<br/>create / list / regenerate / cancel"]
        Docs["/admin/documents/page<br/>upload / list / delete"]
        Docs --> Upload["POST /admin/documents<br/>Tika -> chunk -> embed -> pgvector"]
    end

    RoleCheck -->|USER or ADMIN| UserActions
    RoleCheck -->|ADMIN| AdminActions

    App -.->|401 on API call| Refresh["Browser auto POST /auth/refresh"]
    Refresh -->|ok| App
    Refresh -->|fail| Login

    App --> Logout["POST /auth/logout<br/>revoke refresh + clear cookies"]
    Logout --> Login
```

## Request-time isolation (per authenticated request)

```mermaid
sequenceDiagram
    participant B as Browser
    participant F as JwtAuthFilter
    participant T as TenantContext
    participant C as Controller
    participant S as Service (Chat/Knowledge)
    participant DB as PostgreSQL + pgvector

    B->>F: Request with JWT cookie
    F->>F: Validate JWT, read orgId claim
    F->>T: Bind organization to request scope
    F->>C: Forward authenticated request
    C->>S: Handle (RAG / upload / etc.)
    S->>DB: Org-filtered query (FilterExpressionBuilder)
    DB-->>S: Org-scoped rows only
    S-->>C: Result
    C-->>B: Response
    Note over F,T: TenantContext cleared at end of request
```

## Refresh-token rotation (session lifecycle)

Access tokens are short-lived (15 min); refresh tokens are long-lived (14 days) and
stored only as SHA-256 hashes. Each refresh **rotates** the token: the old one is
revoked and linked to its replacement, so a stolen/replayed old token is rejected.

```mermaid
sequenceDiagram
    participant B as Browser
    participant A as AuthController
    participant R as RefreshTokenService
    participant DB as refresh_tokens table

    Note over B,DB: Login / accept-invite
    B->>A: POST /auth/login (valid credentials)
    A->>R: create(user)
    R->>DB: store new token (SHA-256 hash only)
    A-->>B: Set-Cookie access (15m) + refresh (14d), HttpOnly

    Note over B,DB: Access token expires -> API returns 401
    B->>A: POST /auth/refresh (refresh cookie)
    A->>R: findValidByRawToken(raw)
    R->>DB: lookup by hash, check not revoked/expired
    alt valid
        A->>R: rotate(previousToken)
        R->>DB: revoke old (set revokedAt + replacedByHash)
        R->>DB: store new token hash
        A-->>B: Set-Cookie new access + new refresh
    else invalid / expired / revoked
        A-->>B: 401 -> browser redirects to /login
    end

    Note over B,DB: Logout / password change
    B->>A: POST /auth/logout
    A->>R: revokeByRawToken(raw)
    R->>DB: set revokedAt
    B->>A: POST /auth/change-password
    A->>R: revokeAllForUser(userId)
    R->>DB: revoke all active tokens (log out everywhere)
    A-->>B: clear cookies
```
