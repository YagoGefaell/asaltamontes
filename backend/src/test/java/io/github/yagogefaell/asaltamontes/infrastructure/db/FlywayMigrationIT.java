package io.github.yagogefaell.asaltamontes.infrastructure.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;

/**
 * The test database starts empty, so the context only loads if Flyway applies every migration
 * and Hibernate (ddl-auto=validate) accepts the resulting schema.
 */
class FlywayMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    @Test
    void allMigrationsAreAppliedOnAnEmptyDatabase() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(Arrays.stream(applied).map(MigrationInfo::getState))
                .allMatch(state -> state == MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void hibernateOnlyValidatesTheSchema() {
        assertThat(ddlAuto).isEqualTo("validate");
    }
}
