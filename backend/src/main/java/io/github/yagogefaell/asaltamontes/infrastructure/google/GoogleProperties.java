package io.github.yagogefaell.asaltamontes.infrastructure.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "asaltamontes.google")
public class GoogleProperties {
    private String clientId;
}