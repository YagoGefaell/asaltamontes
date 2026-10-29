package io.github.yagogefaell.asaltamontes.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {

    @Test
    void exposesRetryAfterToCrossOriginJavaScript() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("http://localhost:5173"));
        CorsConfigurationSource source = new CorsConfig(properties).corsConfigurationSource();

        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/events"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getExposedHeaders()).contains("Retry-After");
    }
}
