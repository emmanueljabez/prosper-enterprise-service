# ProsperMentor

A Spring Boot application with Supabase PostgreSQL integration for mentoring platform.

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- Gradle 8.x
- Supabase account and project

### 🔧 Setup

1. **Clone the repository** (if not already done)
   ```bash
   git clone <your-repo-url>
   cd ProsperMentor
   ```

2. **Create Supabase Project**
   - Go to [Supabase Dashboard](https://supabase.com/dashboard)
   - Create a new project
   - Note down your project credentials

3. **Configure Database Connection**
   - Copy the configuration template from `supabase-config.md`
   - Create a `.env` file in the project root with your Supabase credentials:
   ```env
   SUPABASE_DB_URL=jdbc:postgresql://db.your-project-ref.supabase.co:5432/postgres
   SUPABASE_DB_USERNAME=postgres
   SUPABASE_DB_PASSWORD=your-database-password
   ```

4. **Build and Run**
   ```bash
   ./gradlew build
   ./gradlew bootRun
   ```

## 📁 Project Structure

```
src/main/java/com/prosper/prospermentor/
├── config/          # Configuration classes
│   ├── DatabaseConfig.java     # Database configuration
│   └── SecurityConfig.java     # Security configuration
├── entity/          # JPA entities
│   ├── BaseEntity.java         # Base entity with auditing
│   └── User.java              # User entity
├── repository/      # Data access layer
│   └── UserRepository.java    # User repository
├── service/         # Business logic layer
│   └── UserService.java       # User service
└── ProsperMentorApplication.java # Main application class

src/main/resources/
├── db/migration/    # Flyway database migrations
│   └── V1__Create_users_table.sql
└── application.properties     # Application configuration
```

## 🗄️ Database

The application uses **Supabase PostgreSQL** as the primary database with:

- **JPA/Hibernate** for ORM
- **Flyway** for database migrations
- **HikariCP** for connection pooling
- **Auditing** for created/updated timestamps

### Migrations

Database migrations are handled by Flyway and located in `src/main/resources/db/migration/`.

The initial migration creates:
- `users` table with basic user information
- Indexes for performance optimization
- Triggers for automatic timestamp updates

## 🔒 Security

Basic Spring Security configuration is included with:
- BCrypt password encoding
- Form-based authentication
- Role-based access control ready

## 🧪 Testing

Run tests with:
```bash
./gradlew test
```

The project includes Testcontainers for integration testing with PostgreSQL.

## 📝 Configuration

Key configuration properties in `application.properties`:

- **Database**: Connection URL, credentials, pool settings
- **JPA**: Hibernate settings, SQL logging
- **Flyway**: Migration settings
- **Logging**: Application and security logging levels

## 🔧 Development

### Adding New Entities

1. Create entity class extending `BaseEntity`
2. Create repository interface extending `JpaRepository`
3. Create service class for business logic
4. Add Flyway migration script

### Environment Variables

All sensitive configuration uses environment variables with sensible defaults for development.

## 🚀 Deployment

For production deployment:

1. Set environment variables for database connection
2. Enable CSRF protection in SecurityConfig
3. Configure appropriate logging levels
4. Set up proper SSL/TLS configuration

## 📚 Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Supabase Documentation](https://supabase.com/docs)
- [Flyway Documentation](https://flywaydb.org/documentation/)



