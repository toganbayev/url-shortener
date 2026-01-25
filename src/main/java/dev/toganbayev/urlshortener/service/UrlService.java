package dev.toganbayev.urlshortener.service;

import dev.toganbayev.urlshortener.dto.ShortenUrlRequestDto;
import dev.toganbayev.urlshortener.dto.ShortenUrlResponseDto;
import dev.toganbayev.urlshortener.entity.UrlEntity;
import dev.toganbayev.urlshortener.repository.UrlRepository;
import dev.toganbayev.urlshortener.util.UrlUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.net.URI;

@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private final UrlUtils urlUtils;

    public ShortenUrlResponseDto shortenUrl(ShortenUrlRequestDto requestDto) {
        String url = requestDto.getUrl();

        try {
            boolean isValid = urlUtils.isValid(url);

            if (!isValid) {
                throw new RuntimeException("URL is invalid");
            }

            String shortCode = RandomStringUtils.insecure().randomAlphanumeric(8);
            UrlEntity urlEntity = new UrlEntity();
            urlEntity.setMainUrl(url);
            urlEntity.setShortCode(shortCode);
            urlRepository.save(urlEntity);

            return ShortenUrlResponseDto.builder()
                    .shortCode(shortCode)
                    .build();
        } catch (DataIntegrityViolationException e) {
            String shortCode = RandomStringUtils.insecure().randomAlphanumeric(8);
            UrlEntity urlEntity = new UrlEntity();
            urlEntity.setMainUrl(requestDto.getUrl());
            urlEntity.setShortCode(shortCode);
            urlRepository.save(urlEntity);

            return ShortenUrlResponseDto.builder()
                    .shortCode(shortCode)
                    .build();
        }
    }

    public URI getRedirectionUri(String shortCode) {
        String urlToBeParsed = urlRepository.findByShortCode(shortCode)
                .map(UrlEntity::getMainUrl)
                .orElse("/");

        return URI.create(urlToBeParsed);
    }
}
