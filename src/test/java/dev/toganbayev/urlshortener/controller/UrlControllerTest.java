package dev.toganbayev.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.toganbayev.urlshortener.dto.ShortenUrlRequestDto;
import dev.toganbayev.urlshortener.dto.ShortenUrlResponseDto;
import dev.toganbayev.urlshortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller layer tests using MockMvc.
 *
 * Key concepts:
 * - @WebMvcTest: Loads only web layer (controllers, filters, etc.), not full Spring context
 * - MockMvc: Simulates HTTP requests without starting a real server
 * - @MockBean: Creates a mock bean in Spring context (replaces real UrlService)
 * - ObjectMapper: Converts Java objects to JSON (and vice versa)
 * - jsonPath(): Asserts JSON response content using JsonPath expressions
 *
 * These tests verify HTTP behavior: status codes, headers, JSON responses.
 */
@SuppressWarnings("deprecation") // Suppress @MockBean deprecation warning (no replacement in Spring Boot 3.5)
@WebMvcTest(UrlController.class) // Only test UrlController, don't load entire app
class UrlControllerTest {

    // MockMvc - allows us to send fake HTTP requests to the controller
    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper - converts Java objects to JSON strings for request bodies
    @Autowired
    private ObjectMapper objectMapper;

    // Mock the service layer - we're testing the controller, not the service
    @MockBean
    private UrlService urlService;

    /**
     * Happy path: POST /shorten with valid URL should return 200 OK with shortCode.
     *
     * Test flow:
     * 1. Create request DTO with valid URL
     * 2. Mock service to return response with shortCode
     * 3. Send POST request with JSON body
     * 4. Verify HTTP 200 status
     * 5. Verify response is JSON
     * 6. Verify JSON contains correct shortCode using JsonPath
     *
     * JsonPath syntax:
     * - $.shortCode means "root object's shortCode field"
     * - is() is a Hamcrest matcher for equality
     */
    @Test
    void shortenUrl_WithValidRequest_ShouldReturnShortCode() throws Exception {
        // Arrange: Prepare request and expected response
        ShortenUrlRequestDto requestDto = new ShortenUrlRequestDto();
        requestDto.setUrl("https://example.com");

        ShortenUrlResponseDto responseDto = ShortenUrlResponseDto.builder()
                .shortCode("abc12345")
                .build();

        // Mock the service to return our response
        when(urlService.shortenUrl(any(ShortenUrlRequestDto.class))).thenReturn(responseDto);

        // Act & Assert: Send HTTP POST request and verify response
        mockMvc.perform(post("/shorten") // HTTP POST to /shorten endpoint
                        .contentType(MediaType.APPLICATION_JSON) // Tell server we're sending JSON
                        .content(objectMapper.writeValueAsString(requestDto))) // Convert DTO to JSON string
                .andExpect(status().isOk()) // Expect HTTP 200
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Expect JSON response
                .andExpect(jsonPath("$.shortCode", is("abc12345"))); // Expect {"shortCode": "abc12345"}

        // Verify service was called exactly once
        verify(urlService, times(1)).shortenUrl(any(ShortenUrlRequestDto.class));
    }

    /**
     * Error case: POST /shorten with invalid URL should return 500 with error message.
     *
     * Test flow:
     * 1. Mock service to throw RuntimeException
     * 2. Send POST request
     * 3. Verify HTTP 500 status (Internal Server Error)
     * 4. Verify JSON error response from @ControllerAdvice handler
     *
     * This tests that GlobalExceptionHandler catches RuntimeException
     * and returns proper JSON error: {"error": "URL is invalid"}
     */
    @Test
    void shortenUrl_WithInvalidUrl_ShouldReturn500() throws Exception {
        // Arrange: Prepare invalid request
        ShortenUrlRequestDto requestDto = new ShortenUrlRequestDto();
        requestDto.setUrl("invalid-url");

        // Mock service to throw exception
        when(urlService.shortenUrl(any(ShortenUrlRequestDto.class)))
                .thenThrow(new RuntimeException("URL is invalid"));

        // Act & Assert: Send request and verify error response
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isInternalServerError()) // Expect HTTP 500
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Expect JSON
                .andExpect(jsonPath("$.error", is("URL is invalid"))); // Expect {"error": "..."}

        verify(urlService, times(1)).shortenUrl(any(ShortenUrlRequestDto.class));
    }

    /**
     * Edge case: POST /shorten with empty URL field should still be processed.
     *
     * Test flow:
     * 1. Create DTO with null URL (not set)
     * 2. Service will handle validation
     * 3. Verify controller accepts the request
     *
     * Note: In real scenario, this would likely fail validation in service,
     * but this test verifies the controller layer accepts the request format.
     */
    @Test
    void shortenUrl_WithEmptyBody_ShouldProcessRequest() throws Exception {
        // Arrange: Empty DTO (URL not set, will be null)
        ShortenUrlRequestDto requestDto = new ShortenUrlRequestDto();

        ShortenUrlResponseDto responseDto = ShortenUrlResponseDto.builder()
                .shortCode("abc12345")
                .build();

        when(urlService.shortenUrl(any(ShortenUrlRequestDto.class))).thenReturn(responseDto);

        // Act & Assert: Verify request is accepted (validation happens in service)
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk()); // Controller accepts it

        verify(urlService, times(1)).shortenUrl(any(ShortenUrlRequestDto.class));
    }

    /**
     * Happy path: GET /{shortCode} should return 301 redirect.
     *
     * Test flow:
     * 1. Mock service to return original URL
     * 2. Send GET request with shortCode in path
     * 3. Verify HTTP 301 (Moved Permanently) - standard redirect status
     * 4. Verify Location header contains original URL
     *
     * HTTP 301 semantics:
     * - Browser automatically follows the redirect
     * - Location header tells browser where to go
     * - No response body needed
     */
    @Test
    void getRedirectionUri_WithExistingShortCode_ShouldReturn301() throws Exception {
        // Arrange: ShortCode exists, should redirect to original URL
        String shortCode = "abc12345";
        URI redirectUri = URI.create("https://example.com");

        when(urlService.getRedirectionUri(shortCode)).thenReturn(redirectUri);

        // Act & Assert: Send GET request and verify redirect
        mockMvc.perform(get("/{shortCode}", shortCode)) // GET /abc12345
                .andExpect(status().isMovedPermanently()) // HTTP 301
                .andExpect(header().string("Location", "https://example.com")); // Redirect target

        verify(urlService, times(1)).getRedirectionUri(shortCode);
    }

    /**
     * Error case: GET /{shortCode} with non-existing code should redirect to root.
     *
     * Test flow:
     * 1. Mock service to return "/" (not found fallback)
     * 2. Send GET request
     * 3. Verify HTTP 301 redirect to "/"
     *
     * This is a graceful degradation: instead of 404, redirect to homepage.
     */
    @Test
    void getRedirectionUri_WithNonExistingShortCode_ShouldRedirectToRoot() throws Exception {
        // Arrange: ShortCode doesn't exist, service returns "/"
        String shortCode = "notfound";
        URI rootUri = URI.create("/");

        when(urlService.getRedirectionUri(shortCode)).thenReturn(rootUri);

        // Act & Assert: Verify redirect to root
        mockMvc.perform(get("/{shortCode}", shortCode)) // GET /notfound
                .andExpect(status().isMovedPermanently()) // HTTP 301
                .andExpect(header().string("Location", "/")); // Redirect to homepage

        verify(urlService, times(1)).getRedirectionUri(shortCode);
    }

    /**
     * Edge case: ShortCode with special characters should be handled.
     *
     * Test flow:
     * 1. Send GET request with special characters (-_) in path
     * 2. Verify Spring MVC correctly extracts path variable
     * 3. Verify controller processes it normally
     *
     * This tests that @PathVariable correctly handles various characters.
     * In production, you might want to restrict allowed characters.
     */
    @Test
    void getRedirectionUri_WithShortCodeContainingSpecialCharacters_ShouldProcess() throws Exception {
        // Arrange: ShortCode with dashes and underscores
        String shortCode = "abc-123_";
        URI redirectUri = URI.create("https://example.com");

        when(urlService.getRedirectionUri(anyString())).thenReturn(redirectUri);

        // Act & Assert: Verify special characters are handled
        mockMvc.perform(get("/{shortCode}", shortCode)) // GET /abc-123_
                .andExpect(status().isMovedPermanently()) // HTTP 301
                .andExpect(header().string("Location", "https://example.com"));

        verify(urlService, times(1)).getRedirectionUri(shortCode);
    }
}
