# URL Shortener

A modern, production-ready URL shortening service built with Spring Boot 3.5 and Java 21.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Maven](https://img.shields.io/badge/Maven-3.x-C71A36.svg)](https://maven.apache.org/)
[![Lombok](https://img.shields.io/badge/Lombok-latest-pink.svg)](https://projectlombok.org/)
[![JUnit 5](https://img.shields.io/badge/JUnit-5-25A162.svg)](https://junit.org/junit5/)
[![Testcontainers](https://img.shields.io/badge/Testcontainers-latest-1D2C4D.svg)](https://testcontainers.com/)

## Features

- **Fast URL Shortening** - Generate short, unique codes for long URLs
- **Permanent Redirects** - HTTP 301 redirects for optimal SEO
- **Input Validation** - Comprehensive URL validation and sanitization
- **Layered Architecture** - Clean separation of concerns (Controller → Service → Repository)
- **Production Ready** - PostgreSQL database with connection pooling
- **Comprehensive Testing** - 25 tests across unit, slice, and integration layers
- **Docker Support** - OCI image generation with Spring Boot
- **Virtual Threads** - Leveraging Java 21's modern concurrency features

## Technology Stack

| Category | Technology |
|----------|-----------|
| **Language** | Java 21 (LTS) |
| **Framework** | Spring Boot 3.5.10 |
| **Database (Prod)** | PostgreSQL |
| **Database (Test)** | H2 in-memory |
| **ORM** | Spring Data JPA + Hibernate |
| **Build Tool** | Maven |
| **Testing** | JUnit 5, Mockito, Testcontainers |
| **Utilities** | Lombok, Apache Commons Lang3 |

## Quick Start

### Prerequisites

- **Java 21** or higher ([Download](https://www.oracle.com/java/technologies/downloads/#java21))
- **PostgreSQL** ([Download](https://www.postgresql.org/download/))
- **Maven 3.x** (or use included wrapper)
- **Docker** (optional, for Testcontainers integration tests)

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd url-shortener
   ```

2. **Configure the database**

   Edit `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/urlshortener
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   ```

3. **Build the project**
   ```bash
   ./mvnw clean package
   ```

4. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

The service will start on `http://localhost:8080` (default port).

## API Documentation

### Shorten a URL

**Endpoint:** `POST /shorten`

**Request Body:**
```json
{
  "url": "https://www.example.com/very/long/url/path"
}
```

**Response (200 OK):**
```json
{
  "shortCode": "aB3xY9Zq"
}
```

**Example:**
```bash
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.example.com/long/url"}'
```

### Redirect to Original URL

**Endpoint:** `GET /{shortCode}`

**Response:** `301 Moved Permanently`
- `Location` header contains the original URL
- Browser automatically redirects to the original URL

**Example:**
```bash
curl -I http://localhost:8080/aB3xY9Zq
# HTTP/1.1 301 Moved Permanently
# Location: https://www.example.com/long/url
```

### Error Handling

**Invalid URL (500 Internal Server Error):**
```json
{
  "error": "Invalid URL format"
}
```

**Short Code Not Found (404 Not Found):**
```
<!DOCTYPE html>
<html>
  <body>
    <h1>Whitelabel Error Page</h1>
    <p>This application has no explicit mapping for /error...</p>
  </body>
</html>
```

## Architecture

```
┌─────────────────────────────────────────────────┐
│           Controller Layer                      │
│  (REST endpoints, HTTP request/response)        │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│           Service Layer                         │
│  (Business logic, validation, shortCode gen)    │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│           Repository Layer                      │
│  (Spring Data JPA, database access)             │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│           PostgreSQL Database                   │
│  (Persistent storage)                           │
└─────────────────────────────────────────────────┘
```

### Package Structure

```
dev.toganbayev.urlshortener/
├── controller/     # REST endpoints (@RestController)
├── service/        # Business logic (@Service)
├── repository/     # Data access (@Repository)
├── entity/         # JPA entities (@Entity)
├── dto/            # Request/Response DTOs
├── exception/      # Global exception handlers
├── util/           # Utility classes (UrlUtils)
└── config/         # Spring configuration
```

## Testing

The project has **comprehensive test coverage** with 25 tests across three layers:

| Test Type | Count | Technology | Speed |
|-----------|-------|------------|-------|
| **Unit Tests** | 7 | JUnit 5 + Mockito | Fast |
| **Slice Tests** | 13 | @WebMvcTest, @DataJpaTest (H2) | Fast |
| **Integration Tests** | 5 | @SpringBootTest + Testcontainers | Slow |

### Run Tests

```bash
# Run all tests (requires Docker for Testcontainers)
./mvnw test

# Run only fast tests (no Docker needed)
./mvnw test -Dtest='!UrlShortenerIntegrationTest'

# Run specific test class
./mvnw test -Dtest=UrlServiceTest

# Run with coverage
./mvnw test jacoco:report
```

### Test Strategy

- **Unit Tests** - Fast, isolated tests with mocked dependencies
- **Slice Tests** - Test individual layers (controller, service, repository) with minimal Spring context
- **Integration Tests** - End-to-end tests with real PostgreSQL via Testcontainers

See [TESTING_STRATEGY.md](TESTING_STRATEGY.md) for detailed testing documentation.

## Development

### Build Commands

```bash
# Clean build
./mvnw clean package

# Skip tests
./mvnw clean package -DskipTests

# Create Docker image
./mvnw spring-boot:build-image

# View dependency tree
./mvnw dependency:tree

# Check for updates
./mvnw versions:display-dependency-updates
```

### Database Setup

**Production Database (PostgreSQL):**
1. Create database: `createdb urlshortener`
2. Configure connection in `application.properties`
3. Hibernate will auto-create tables on first run

**Test Database (H2):**
- Automatically configured for tests
- In-memory database (no setup needed)
- Created/destroyed for each test run

### Code Style

- **Indentation:** 4 spaces
- **Lombok:** Used extensively to reduce boilerplate
- **Validation:** Jakarta Validation annotations on DTOs
- **Exception Handling:** Centralized via `@ControllerAdvice`

### Key Components

**UrlUtils** (`util/UrlUtils.java`)
- Validates URLs (http/https schemes only)
- Rejects malformed URLs and relative URIs

**GlobalExceptionHandler** (`exception/GlobalExceptionHandler.java`)
- Centralized error handling with `@ControllerAdvice`
- Returns structured JSON error responses

**UrlService** (`service/UrlService.java`)
- Generates 8-character alphanumeric short codes
- Handles duplicate collision detection
- Validates URLs before shortening

## Docker Support

Build and run with Docker:

```bash
# Build OCI image
./mvnw spring-boot:build-image

# Run with Docker Compose (create docker-compose.yml first)
docker-compose up
```

## Roadmap

- [ ] Custom short code support (user-defined aliases)
- [ ] Analytics and click tracking
- [ ] Expiration dates for short URLs
- [ ] Rate limiting and API authentication
- [ ] Admin dashboard
- [ ] Metrics and monitoring (Prometheus/Grafana)

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Write tests for new functionality
4. Ensure all tests pass (`./mvnw test`)
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

## Contact

Toganbayev - [@toganbayev](https://github.com/toganbayev)

Project Link: [https://github.com/toganbayev/url-shortener](https://github.com/toganbayev/url-shortener)

## Acknowledgments

- [Spring Boot](https://spring.io/projects/spring-boot) - Application framework
- [PostgreSQL](https://www.postgresql.org/) - Database
- [Testcontainers](https://www.testcontainers.org/) - Integration testing
- [Lombok](https://projectlombok.org/) - Boilerplate reduction
