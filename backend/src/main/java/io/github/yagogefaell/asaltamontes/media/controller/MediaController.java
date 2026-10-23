package io.github.yagogefaell.asaltamontes.media.controller;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.dto.MediaDto;
import io.github.yagogefaell.asaltamontes.media.service.MediaService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /** Any authenticated user can upload (the app-wide rule: everything outside /auth is authenticated). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaDto> upload(
            @AuthenticationPrincipal UserAccount currentUser,
            @RequestParam("purpose") MediaPurpose purpose,
            @RequestParam("file") MultipartFile file) {
        MediaDto created = mediaService.upload(currentUser, purpose, file);
        return ResponseEntity.created(URI.create(created.urls().large())).body(created);
    }
}
