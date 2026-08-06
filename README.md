# Doc-AI Authentication & User Management

This project uses invite-only authentication with secure cookie sessions and multi-tenant organization support.

## API Endpoints

### Authentication

- `POST /auth/login` - User login
- `POST /auth/accept-invite` - Accept invite and create account
- `POST /auth/refresh` - Refresh session tokens
- `POST /auth/logout` - Logout
- `GET /auth/me` - Get current user info
- `POST /auth/change-password` - Change password
- `POST /auth/invites` - Create user invite (ROLE_ADMIN only)
- `GET /auth/invites` - List all invites (ROLE_ADMIN only)

## UI Routes

| Route | Access | Purpose |
|-------|--------|---------|
| `/` | Public | Redirects to `/login` |
| `/login` | Public | Login form |
| `/accept-invite` | Public | Accept invite & create account |
| `/app` | Protected | Main chat and RAG workspace |
| `/change-password` | Protected | Password rotation screen |
| `/admin/invites` | Admin only | Manage user invites |

## Invite Workflow

### For Admins: Create an Invite

1. Navigate to `/admin/invites`
2. Fill in the email, role (User/Admin), and optionally the organization
3. Click "Create & Copy Invite Link"
4. The invite link is automatically copied to clipboard
5. Share the link with the user via email or secure channel

### For Users: Accept an Invite

1. Receive invite link: `https://yourapp.com/accept-invite?token=<invite-token>`
2. Click the link (or paste the token on the accept-invite page)
3. Enter your full name and create a password
4. Submit to create your account
5. Automatically logged in and redirected to `/app`

## Bootstrap Admin Setup

Bootstrap is disabled by default. Enable it **once** to seed the first admin user.

### Step 1: Set Environment Variables

```bash
export APP_SECURITY_BOOTSTRAP_ADMIN_ENABLED=true
export APP_SECURITY_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
export APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD=YourSecurePassword123!
export APP_SECURITY_BOOTSTRAP_ADMIN_NAME="System Admin"
export APP_SECURITY_BOOTSTRAP_ORG_ID=default-org
```

### Step 2: Start Application

```bash
mvn spring-boot:run
# or with Docker Compose
docker-compose up
```

### Step 3: Login & Verify

- Navigate to `/login`
- Login with the bootstrap credentials
- After successful login, **disable bootstrap immediately**:

```bash
export APP_SECURITY_BOOTSTRAP_ADMIN_ENABLED=false
# Restart the application
```

## Cookie Security

- Cookies are **HttpOnly** - JavaScript cannot access them
- Cookies are **Secure** by default (HTTPS only)
- **SameSite=Lax** for CSRF protection

### Local HTTP Development

For localhost development without HTTPS:

```bash
export APP_SECURITY_COOKIE_SECURE=false
```

## Key Features

- ✅ **Invite-only onboarding** - Control who joins
- ✅ **Expiring invite tokens** - Security by default
- ✅ **Multi-tenant organization support** - Isolate users by org
- ✅ **Role-based access** - ADMIN and USER roles
- ✅ **Token rotation** - Refresh token rotation on every use
- ✅ **Password management** - Force password change on first login
- ✅ **Admin invite dashboard** - Create and track invites with UI
