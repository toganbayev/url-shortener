package dev.toganbayev.urlshortener.service;

import dev.toganbayev.urlshortener.dto.ShortenUrlRequestDto;
import dev.toganbayev.urlshortener.dto.ShortenUrlResponseDto;
import dev.toganbayev.urlshortener.entity.UrlEntity;
import dev.toganbayev.urlshortener.repository.UrlRepository;
import dev.toganbayev.urlshortener.util.UrlUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UrlService using Mockito.
 *
 * Key concepts:
 * - @ExtendWith(MockitoExtension.class): Enables Mockito in JUnit 5
 * - @Mock: Creates a mock object (fake dependency)
 * - @InjectMocks: Creates instance and injects all @Mock dependencies into it
 * - when().thenReturn(): Defines behavior of mock methods
 * - verify(): Checks if a method was called (and how many times)
 */
@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    // Mock the repository - we don't want to hit a real database in unit tests
    @Mock
    private UrlRepository urlRepository;

    // Mock the utility - we don't want to test UrlUtils here (already tested separately)
    @Mock
    private UrlUtils urlUtils;

    // The service we're testing - Mockito will inject the mocks above into this
    @InjectMocks
    private UrlService urlService;

    // Test data - will be initialized before each test
    private ShortenUrlRequestDto requestDto;

    /**
     * Runs before each test method.
     * Sets up common test data to avoid repetition.
     */
    @BeforeEach
    void setUp() {
        requestDto = new ShortenUrlRequestDto();
        requestDto.setUrl("https://example.com");
    }

    /**
     * Happy path test: Valid URL should be shortened successfully.
     *
     * Test flow:
     * 1. Mock urlUtils.isValid() to return true (URL passes validation)
     * 2. Mock repository.save() to return a saved entity with ID
     * 3. Call shortenUrl()
     * 4. Verify response contains an 8-character shortCode
     * 5. Verify that isValid() and save() were called exactly once
     */
    @Test
    void shortenUrl_WithValidUrl_ShouldReturnShortCode() {
        // Arrange: Set up mock behavior
        // When isValid() is called with any string, return true
        when(urlUtils.isValid(anyString())).thenReturn(true);

        // Create a fake saved entity to be returned by repository
        UrlEntity savedEntity = new UrlEntity();
        savedEntity.setId(1L);
        savedEntity.setMainUrl("https://example.com");
        savedEntity.setShortCode("abc12345");

        // When save() is called with any UrlEntity, return our fake entity
        when(urlRepository.save(any(UrlEntity.class))).thenReturn(savedEntity);

        // Act: Call the method we're testing
        ShortenUrlResponseDto response = urlService.shortenUrl(requestDto);

        // Assert: Verify the results
        assertNotNull(response); // Response should not be null
        assertNotNull(response.getShortCode()); // shortCode should not be null
        assertEquals(8, response.getShortCode().length()); // shortCode should be 8 characters

        // Verify that our mocked methods were called the expected number of times
        verify(urlUtils, times(1)).isValid("https://example.com");
        verify(urlRepository, times(1)).save(any(UrlEntity.class));
    }

    /**
     * Error case: Invalid URL should throw RuntimeException.
     *
     * Test flow:
     * 1. Mock urlUtils.isValid() to return false (URL fails validation)
     * 2. Call shortenUrl() and expect it to throw RuntimeException
     * 3. Verify the exception message is correct
     * 4. Verify repository.save() was NEVER called (validation failed first)
     */
    @Test
    void shortenUrl_WithInvalidUrl_ShouldThrowRuntimeException() {
        // Arrange: Make validation fail
        when(urlUtils.isValid(anyString())).thenReturn(false);

        // Act & Assert: Expect an exception to be thrown
        // assertThrows captures the exception and lets us verify it
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            urlService.shortenUrl(requestDto);
        });

        // Verify the exception has the correct message
        assertEquals("URL is invalid", exception.getMessage());

        // Verify validation was called once
        verify(urlUtils, times(1)).isValid("https://example.com");

        // Verify save was NEVER called (because validation failed)
        verify(urlRepository, never()).save(any(UrlEntity.class));
    }

    /**
     * Edge case: Duplicate shortCode collision should trigger retry logic.
     *
     * Test flow:
     * 1. Mock urlUtils.isValid() to return true
     * 2. Mock repository.save() to throw exception first time (duplicate),
     *    then succeed on second attempt
     * 3. Call shortenUrl()
     * 4. Verify that save() was called TWICE (first failed, second succeeded)
     *
     * This tests the service's collision handling logic.
     */
    @Test
    void shortenUrl_WithDuplicateShortCode_ShouldRetryWithNewShortCode() {
        // Arrange: Set up validation to pass
        when(urlUtils.isValid(anyString())).thenReturn(true);

        // First save() call throws exception (duplicate shortCode in database)
        // Second save() call succeeds
        // This simulates the rare case where random shortCode already exists
        when(urlRepository.save(any(UrlEntity.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"))
                .thenReturn(new UrlEntity()); // Second attempt succeeds

        // Act: Call the method
        ShortenUrlResponseDto response = urlService.shortenUrl(requestDto);

        // Assert: Verify response is valid
        assertNotNull(response);
        assertNotNull(response.getShortCode());
        assertEquals(8, response.getShortCode().length());

        // Verify save() was called TWICE (first attempt failed, second succeeded)
        verify(urlRepository, times(2)).save(any(UrlEntity.class));
    }

    /**
     * Happy path: ShortCode exists in database, should redirect to original URL.
     *
     * Test flow:
     * 1. Mock repository to return an entity when shortCode is found
     * 2. Call getRedirectionUri()
     * 3. Verify it returns the original URL as a URI
     */
    @Test
    void getRedirectionUri_WithExistingShortCode_ShouldReturnCorrectUri() {
        // Arrange: Create a fake entity that exists in database
        String shortCode = "abc12345";
        UrlEntity urlEntity = new UrlEntity();
        urlEntity.setShortCode(shortCode);
        urlEntity.setMainUrl("https://example.com");

        // When findByShortCode() is called, return Optional containing our entity
        // Optional.of() = "value is present"
        when(urlRepository.findByShortCode(shortCode)).thenReturn(Optional.of(urlEntity));

        // Act: Get the redirection URI
        URI result = urlService.getRedirectionUri(shortCode);

        // Assert: Should return the original URL
        assertNotNull(result);
        assertEquals("https://example.com", result.toString());
        verify(urlRepository, times(1)).findByShortCode(shortCode);
    }

    /**
     * Error case: ShortCode not found in database, should redirect to root.
     *
     * Test flow:
     * 1. Mock repository to return empty Optional (not found)
     * 2. Call getRedirectionUri()
     * 3. Verify it returns "/" (fallback behavior)
     */
    @Test
    void getRedirectionUri_WithNonExistingShortCode_ShouldReturnRootUri() {
        // Arrange: ShortCode doesn't exist in database
        String shortCode = "notfound";

        // When findByShortCode() is called, return empty Optional
        // Optional.empty() = "no value present"
        when(urlRepository.findByShortCode(shortCode)).thenReturn(Optional.empty());

        // Act: Get the redirection URI
        URI result = urlService.getRedirectionUri(shortCode);

        // Assert: Should return "/" as fallback
        assertNotNull(result);
        assertEquals("/", result.toString());
        verify(urlRepository, times(1)).findByShortCode(shortCode);
    }

    /**
     * Validation test: Verify shortCode format is correct.
     *
     * Test flow:
     * 1. Use Answer to inspect the entity being saved
     * 2. Verify the shortCode is exactly 8 characters
     * 3. Verify it contains only alphanumeric characters (a-z, A-Z, 0-9)
     *
     * This uses Answer instead of thenReturn to inspect the actual arguments.
     */
    @Test
    void shortenUrl_ShouldGenerateEightCharacterShortCode() {
        // Arrange: Set up validation to pass
        when(urlUtils.isValid(anyString())).thenReturn(true);

        // Use Answer to inspect what gets saved
        // invocation.getArgument(0) gets the first parameter passed to save()
        when(urlRepository.save(any(UrlEntity.class))).thenAnswer(invocation -> {
            UrlEntity entity = invocation.getArgument(0);
            entity.setId(1L); // Simulate database auto-increment
            return entity;
        });

        // Act: Create a shortened URL
        ShortenUrlResponseDto response = urlService.shortenUrl(requestDto);

        // Assert: Verify shortCode format
        assertNotNull(response.getShortCode());
        assertEquals(8, response.getShortCode().length()); // Exactly 8 characters

        // Regex: [a-zA-Z0-9]{8} means "exactly 8 alphanumeric characters"
        assertTrue(response.getShortCode().matches("[a-zA-Z0-9]{8}"));
    }
}
