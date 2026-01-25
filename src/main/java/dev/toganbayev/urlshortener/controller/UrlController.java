package dev.toganbayev.urlshortener.controller;

import dev.toganbayev.urlshortener.dto.ShortenUrlRequestDto;
import dev.toganbayev.urlshortener.dto.ShortenUrlResponseDto;
import dev.toganbayev.urlshortener.service.UrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    @PostMapping("/shorten")
//    @PutMapping
    public ShortenUrlResponseDto shortenUrl(@RequestBody ShortenUrlRequestDto requestDto) {
        return urlService.shortenUrl(requestDto);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> getRedirectionUri(@PathVariable String shortCode) {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY.value())
                .location(urlService.getRedirectionUri(shortCode))
                .build();
    }

}
