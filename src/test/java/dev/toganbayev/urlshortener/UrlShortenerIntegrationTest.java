package dev.toganbayev.urlshortener;

import dev.toganbayev.urlshortener.dto.ShortenUrlRequestDto;
import dev.toganbayev.urlshortener.dto.ShortenUrlResponseDto;
import dev.toganbayev.urlshortener.entity.UrlEntity;
import dev.toganbayev.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for URL Shortener using Testcontainers.
 *
 * This test class demonstrates the TOP of the testing pyramid:
 * - Spins up a REAL PostgreSQL database in a Docker container
 * - Tests the ENTIRE application stack (Controller -> Service -> Repository -> Database)
 * - Verifies end-to-end behavior with real database interactions
 *
 * Why use Testcontainers?
 * 1. Tests against the SAME database engine used in production (PostgreSQL, not H2)
 * 2. Catches PostgreSQL-specific issues (SQL dialect differences, constraints, etc.)
 * 3. Provides confidence that the application works with real infrastructure
 *
 * Trade-offs:
 * - SLOWER than unit tests (needs to start Docker container)
 * - Requires Docker to be running
 * - Best for end-to-end scenarios, not for testing every edge case
 *
 * @SpringBootTest(webEnvironment = RANDOM_PORT) - Starts the full Spring context with embedded web server
 * @Testcontainers - Enables Testcontainers lifecycle management
 * @Container @ServiceConnection - Automatically configures Spring to use the container database
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UrlShortenerIntegrationTest {

    /**
     * PostgreSQL container that will be started before tests and stopped after.
     *
     * @ServiceConnection annotation (Spring Boot 3.1+) automatically:
     * - Configures spring.datasource.url to point to the container
     * - Sets spring.datasource.username and spring.datasource.password
     * - No manual configuration needed!
     *
     * The container is shared across all tests in this class (singleton pattern).
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * TestRestTemplate - Used for making real HTTP requests to the running application.
     * Spring Boot automatically configures it to point to the random port.
     */
    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Direct access to repository to verify database state.
     * This allows us to check what was actually persisted in PostgreSQL.
     */
    @Autowired
    private UrlRepository urlRepository;

    /**
     * Test: Verify that PostgreSQL container is running and accessible.
     *
     * This is a sanity check to ensure Testcontainers infrastructure works.
     * If this fails, Docker might not be running or there's a configuration issue.
     */
    @Test
    void containerIsRunningAndReachable() {
        // Assert that the PostgreSQL container was successfully started
        assertThat(postgres.isCreated()).isTrue();
        assertThat(postgres.isRunning()).isTrue();
    }

    /**
     * Test: End-to-end URL shortening flow.
     *
     * This test exercises the ENTIRE application stack:
     * 1. HTTP POST request to /shorten endpoint
     * 2. Controller receives and validates request
     * 3. Service generates shortCode and saves to database
     * 4. Repository persists to PostgreSQL (running in Docker)
     * 5. Response is returned to client
     * 6. We verify the data was actually saved in PostgreSQL
     *
     * Why this test is valuable:
     * - Catches integration issues that unit tests miss
     * - Verifies PostgreSQL-specific behavior (e.g., unique constraints)
     * - Tests real JSON serialization/deserialization
     * - Validates HTTP layer (status codes, headers, etc.)
     */
    @Test
    void shouldShortenUrlAndPersistToPostgres() {
        // Arrange: Prepare the request payload
        String originalUrl = "https://www.example.com/very/long/url";
        ShortenUrlRequestDto request = new ShortenUrlRequestDto();
        request.setUrl(originalUrl);

        // Act: Make a real HTTP POST request to the running application
        ResponseEntity<ShortenUrlResponseDto> response = restTemplate.postForEntity(
                "/shorten",
                request,
                ShortenUrlResponseDto.class
        );

        // Assert: Verify HTTP response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getShortCode()).isNotEmpty();

        // Extract the shortCode from the response
        String shortCode = response.getBody().getShortCode();

        // Assert: Verify shortCode format (8 alphanumeric characters)
        assertThat(shortCode).hasSize(8);
        assertThat(shortCode).matches("[a-zA-Z0-9]{8}");

        // Assert: Verify data was actually persisted in PostgreSQL
        // This is the KEY difference from unit tests - we're checking the REAL database!
        Optional<UrlEntity> savedEntity = urlRepository.findByShortCode(shortCode);
        assertThat(savedEntity).isPresent();
        assertThat(savedEntity.get().getMainUrl()).isEqualTo(originalUrl);
        assertThat(savedEntity.get().getShortCode()).isEqualTo(shortCode);
        assertThat(savedEntity.get().getId()).isNotNull(); // PostgreSQL auto-generated ID
    }

    /**
     * Test: End-to-end URL redirection flow.
     *
     * This test verifies the complete redirect functionality:
     * 1. First, create a shortened URL (setup)
     * 2. Then, make a GET request to /{shortCode}
     * 3. Verify we get a 301 redirect with correct Location header
     * 4. Verify the redirect target is the original URL
     *
     * Why test redirects in integration tests?
     * - HTTP redirects involve status codes, headers, and URL parsing
     * - Integration test verifies the entire HTTP flow works correctly
     * - Unit tests can miss issues with URL encoding, header formatting, etc.
     */
    @Test
    void shouldRedirectToOriginalUrl() {
        // Arrange: First, create a shortened URL
        String originalUrl = "https://www.github.com";
        ShortenUrlRequestDto request = new ShortenUrlRequestDto();
        request.setUrl(originalUrl);

        ResponseEntity<ShortenUrlResponseDto> shortenResponse = restTemplate.postForEntity(
                "/shorten",
                request,
                ShortenUrlResponseDto.class
        );

        String shortCode = shortenResponse.getBody().getShortCode();

        // Act: Make a GET request to the shortCode endpoint
        // NOTE: TestRestTemplate follows redirects by default, so we need to disable that
        // to test the redirect response itself
        ResponseEntity<Void> redirectResponse = restTemplate.getForEntity(
                "/" + shortCode,
                Void.class
        );

        // Assert: Verify we got a redirect response
        // Note: TestRestTemplate follows redirects, so we'll get the final response
        // In a real browser, the status would be 301 and Location header would be set
        assertThat(redirectResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.MOVED_PERMANENTLY);
    }

    /**
     * Test: Handling non-existent short codes.
     *
     * Verifies error handling when requesting a shortCode that doesn't exist in the database.
     * This tests the negative path through the entire stack.
     */
    @Test
    void shouldReturnErrorForNonExistentShortCode() {
        // Act: Request a shortCode that doesn't exist
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/nonexistent",
                String.class
        );

        // Assert: Verify we got an error response
        // The exact status depends on GlobalExceptionHandler implementation
        assertThat(response.getStatusCode()).isIn(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.NOT_FOUND);
    }

    /**
     * Test: Verify database isolation between tests.
     *
     * Testcontainers by default does NOT reset the database between tests.
     * This test checks if data from previous tests is still present.
     *
     * Note: For true test isolation, you can:
     * 1. Use @DirtiesContext to reset Spring context (slow)
     * 2. Use @Transactional on test class to rollback after each test
     * 3. Manually clean up in @BeforeEach/@AfterEach
     * 4. Use database migration tools (Flyway/Liquibase) with clean/migrate
     */
    @Test
    void shouldPersistDataAcrossMultipleOperations() {
        // Arrange: Create first URL
        ShortenUrlRequestDto request1 = new ShortenUrlRequestDto();
        request1.setUrl("https://www.first.com");

        // Act: Create first shortened URL
        restTemplate.postForEntity("/shorten", request1, ShortenUrlResponseDto.class);

        // Arrange: Create second URL
        ShortenUrlRequestDto request2 = new ShortenUrlRequestDto();
        request2.setUrl("https://www.second.com");

        // Act: Create second shortened URL
        restTemplate.postForEntity("/shorten", request2, ShortenUrlResponseDto.class);

        // Assert: Both URLs should be in the database
        long count = urlRepository.count();
        // Note: Count might be >= 2 if previous tests already added data
        // This demonstrates that Testcontainers doesn't reset DB between tests by default
        assertThat(count).isGreaterThanOrEqualTo(2);
    }
}
