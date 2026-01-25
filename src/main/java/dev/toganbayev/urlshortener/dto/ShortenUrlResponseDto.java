package dev.toganbayev.urlshortener.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ShortenUrlResponseDto {

    private String shortCode;

}
