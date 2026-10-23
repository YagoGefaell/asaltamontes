package io.github.yagogefaell.asaltamontes.media.dto;

/** Public URLs of the three variants of an image (longest side 128, 640 and 1280 px). */
public record MediaUrlsDto(String thumb, String medium, String large) {
}
