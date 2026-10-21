package io.github.yagogefaell.asaltamontes.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the maintenance jobs everywhere except in tests, so they never run in the middle of a test.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
