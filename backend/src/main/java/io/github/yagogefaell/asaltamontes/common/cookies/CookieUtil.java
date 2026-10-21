package io.github.yagogefaell.asaltamontes.common.cookies;

import org.springframework.http.ResponseCookie;
import org.springframework.lang.NonNull;

public final class CookieUtil {

    private CookieUtil() {}

    /* public static ResponseCookie accessToken(String token) {
        return ResponseCookie.from("accessToken", token)
            .httpOnly(true)
            .secure(false) // Set to true in production (https)
            .sameSite("Strict")
            .path("/")
            .maxAge(60 * 60)
            .build();
    } */

    public static ResponseCookie refreshToken(String token, CookieProperties properties) {
        return ResponseCookie.from("refreshToken", token)
            .httpOnly(true)
            .secure(properties.isSecure())
            .sameSite(properties.getSameSite())
            .path("/")
            .maxAge(7 * 24 * 60 * 60)
            .build();
    }

    public static ResponseCookie clear(@NonNull String name, @NonNull String path, CookieProperties properties) {
        return ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(properties.isSecure())
            .sameSite(properties.getSameSite())
            .path(path)
            .maxAge(0)
            .build();
    }
}
