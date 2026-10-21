package io.github.yagogefaell.asaltamontes.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base class for integration tests: full application context against a real PostgreSQL in Docker.
 *
 * The container is started once per JVM and shared by every test class (same Spring context too),
 * so tests must not rely on an empty database: use {@link TestDataFactory} for unique data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestDataFactory.class, AuthTestHelper.class})
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TestDataFactory testData;

    @Autowired
    protected AuthTestHelper auth;

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
