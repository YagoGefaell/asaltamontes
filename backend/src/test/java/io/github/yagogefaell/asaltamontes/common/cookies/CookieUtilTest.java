package io.github.yagogefaell.asaltamontes.common.cookies;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class CookieUtilTest {

    @Test
    void refreshToken_isSecureWhenEnabled() {
        CookieProperties properties = new CookieProperties();
        properties.setSecure(true);

        ResponseCookie cookie = CookieUtil.refreshToken("token", properties);

        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.toString()).contains("Secure");
    }

    @Test
    void refreshToken_isNotSecureByDefault() {
        ResponseCookie cookie = CookieUtil.refreshToken("token", new CookieProperties());

        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }

    @Test
    void clear_usesTheSameAttributes() {
        CookieProperties properties = new CookieProperties();
        properties.setSecure(true);
        properties.setSameSite("Lax");

        ResponseCookie cookie = CookieUtil.clear("refreshToken", "/", properties);

        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge().isZero()).isTrue();
    }
}
