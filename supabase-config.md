# Supabase Auth Integration Guide

## Complete Setup Guide

Your Spring Boot application is now configured to use **Supabase Auth with JWT tokens**. Here's how to complete the setup:

## Environment Variables Setup

Create a `.env` file in your project root with the following variables:

```env
# Supabase Database Configuration
SUPABASE_DB_URL=jdbc:postgresql://db.your-project-ref.supabase.co:5432/postgres
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=your-database-password

# Supabase Auth Configuration
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-anon-key
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
SUPABASE_JWT_SECRET=your-jwt-secret

# Optional Configuration
SHOW_SQL=false
LOG_LEVEL=INFO

# Connection Pool Settings (optional - defaults are provided)
DB_POOL_SIZE=10
DB_MIN_IDLE=5
DB_IDLE_TIMEOUT=300000
DB_CONNECTION_TIMEOUT=20000
DB_MAX_LIFETIME=1200000
```

## How to Get Your Supabase Credentials

1. Go to your [Supabase Dashboard](https://supabase.com/dashboard)
2. Select your project

### Database Credentials:
3. Navigate to **Settings** → **Database**
4. Find the **Connection string** section
5. Use the **JDBC** connection string format
6. Your URL should look like: `jdbc:postgresql://db.{project-ref}.supabase.co:5432/postgres`

### Auth Credentials:
7. Navigate to **Settings** → **API**
8. Copy the following values:
   - **Project URL** → `SUPABASE_URL`
   - **anon public** key → `SUPABASE_ANON_KEY`
   - **service_role** key → `SUPABASE_SERVICE_ROLE_KEY`
   - **JWT Secret** → `SUPABASE_JWT_SECRET`

## API Endpoints

Your application now provides the following endpoints:

### Public Endpoints (No Authentication Required)
- `GET /api/public/health` - Health check
- `GET /api/public/info` - API information

### Authenticated Endpoints (Require JWT Token)
- `GET /api/auth/profile` - Get current user profile
- `GET /api/auth/user-details` - Get detailed user info from Supabase
- `PUT /api/auth/update-metadata` - Update user metadata

### Admin Endpoints (Require ADMIN role)
- `GET /api/admin/users` - List all users
- `GET /api/admin/users/{userId}` - Get user by ID
- `POST /api/admin/users` - Create new user
- `PUT /api/admin/users/{userId}/password` - Update user password
- `PUT /api/admin/users/{userId}/metadata` - Update user metadata
- `DELETE /api/admin/users/{userId}` - Delete user

## How to Use

### 1. Frontend Authentication
Users authenticate using Supabase Auth in your frontend application:

```javascript
// Example with Supabase JavaScript client
import { createClient } from '@supabase/supabase-js'

const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY)

// Sign up
const { data, error } = await supabase.auth.signUp({
  email: 'user@example.com',
  password: 'password',
  options: {
    data: {
      role: 'MENTEE' // Custom metadata
    }
  }
})

// Sign in
const { data, error } = await supabase.auth.signInWithPassword({
  email: 'user@example.com',
  password: 'password'
})

// Get access token for API calls
const { data: { session } } = await supabase.auth.getSession()
const accessToken = session?.access_token
```

### 2. API Calls with JWT Token
Include the JWT token in the Authorization header:

```javascript
// Example API call
fetch('/api/auth/profile', {
  headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  }
})
```

### 3. User Roles
Set user roles in Supabase user metadata:
- `ADMIN` - Full access to admin endpoints
- `MENTOR` - Access to mentor-specific features
- `MENTEE` - Basic user access

## Database Schema

The application maintains a local `users` table synchronized with Supabase Auth:
- `supabase_user_id` - Links to Supabase Auth user
- `email` - User email (synced from Supabase)
- `role` - User role for authorization
- `first_name`, `last_name` - Optional user profile data
- `phone`, `avatar_url`, `bio` - Additional profile fields
- `last_login_at` - Track user activity

## Security Features

- **JWT Token Validation** - All tokens are verified using your JWT secret
- **Role-based Access Control** - Endpoints protected by user roles
- **CORS Configuration** - Configured for frontend integration
- **Stateless Authentication** - No server-side sessions required

## Testing

Test the setup:

1. **Health Check**: `GET /api/public/health`
2. **Create User** in Supabase Dashboard
3. **Get Token** from frontend auth
4. **Test Protected Endpoint**: `GET /api/auth/profile` with Bearer token

## Production Considerations

- Set specific CORS origins instead of wildcards
- Use environment variables for all sensitive data
- Enable CSRF protection if needed
- Configure proper logging levels
- Set up monitoring for auth failures

## Alternative: Direct Configuration

If you prefer not to use environment variables, you can directly update the `application.properties` file with your Supabase credentials.

## Security Note

- Never commit your actual credentials to version control
- Add `.env` to your `.gitignore` file
- Use environment variables in production deployments
- Keep your service role key secure - it has admin privileges
