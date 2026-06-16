package com.obank.booking;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    private static final boolean USE_EXTERNAL = System.getenv("TEST_POSTGRES_URL") != null;

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES;
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS;

    static {
        if (USE_EXTERNAL) {
            POSTGRES = null;
            REDIS = null;
        } else {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("booking_test")
                    .withUsername("booking")
                    .withPassword("booking");
            REDIS = new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);
            POSTGRES.start();
            REDIS.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL) {
            registry.add("spring.datasource.url",
                    () -> System.getenv("TEST_POSTGRES_URL"));
            registry.add("spring.datasource.username",
                    () -> System.getenv().getOrDefault("TEST_POSTGRES_USER", "booking"));
            registry.add("spring.datasource.password",
                    () -> System.getenv().getOrDefault("TEST_POSTGRES_PASSWORD", "booking"));
            registry.add("spring.data.redis.host",
                    () -> System.getenv().getOrDefault("TEST_REDIS_HOST", "localhost"));
            registry.add("spring.data.redis.port",
                    () -> System.getenv().getOrDefault("TEST_REDIS_PORT", "6379"));
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.data.redis.host", REDIS::getHost);
            registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        }
    }

    @LocalServerPort
    protected int port;

    protected RestTemplate restTemplate() {
        return new RestTemplateBuilder()
                .rootUri("http://localhost:" + port)
                .build();
    }
}
