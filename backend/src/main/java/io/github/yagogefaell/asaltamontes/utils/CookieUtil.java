package io.github.yagogefaell.asaltamontes.utils;

import org.springframework.http.ResponseCookie;

public final class CookieUtil {

    private CookieUtil() {}

    public static ResponseCookie accessToken(String token) {
        return ResponseCookie.from("accessToken", token)
            .httpOnly(true)
            .secure(false) // Set to true in production (https)
            .sameSite("Strict")
            .path("/")
            .maxAge(60 * 60)
            .build();
    }

    public static ResponseCookie refreshToken(String token) {
        return ResponseCookie.from("refreshToken", token)
            .httpOnly(true)
            .secure(false) // Set to true in production (https)
            .sameSite("Strict")
            .path("/")
            .maxAge(7 * 24 * 60 * 60)
            .build();
    }

    public static ResponseCookie clear(String name, String path) {
        return ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path(path)
            .maxAge(0)
            .build();
    }
}
