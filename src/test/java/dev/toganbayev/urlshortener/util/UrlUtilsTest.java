package dev.toganbayev.urlshortener.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UrlUtils class.
 * These tests verify URL validation logic without Spring context.
 */
public class UrlUtilsTest {

    // Create an instance of UrlUtils to test
    // No mocking needed - this is a simple utility class
    private UrlUtils urlUtils = new UrlUtils();

    /**
     * Tests the isValid() method with various URL formats.
     *
     * Test cases:
     * 1. Relative URI without protocol - should be INVALID
     * 2. Malformed URL with protocol keyword but no :// - should be INVALID
     * 3. Valid HTTP URL - should be VALID
     * 4. Valid HTTPS URL - should be VALID
     * 5. Invalid protocol (typo in "http") - should be INVALID
     * 6. Null input - should be INVALID (doesn't throw NPE)
     */
    @Test
    void test_isValid() {
        // Invalid: relative URI (no http:// or https://)
        assertFalse(urlUtils.isValid("spring boot diaries"));

        // Invalid: has "http" keyword but not a valid URL
        assertFalse(urlUtils.isValid("http spring boot diaries"));

        // Valid: proper HTTP URL
        assertTrue(urlUtils.isValid("http://facebook.com"));

        // Valid: proper HTTPS URL (secure)
        assertTrue(urlUtils.isValid("https://facebook.com"));

        // Invalid: malformed protocol (should be "http://")
        assertFalse(urlUtils.isValid("htt://"));

        // Invalid: null input should be handled gracefully
        assertFalse(urlUtils.isValid(null));
    }

}
