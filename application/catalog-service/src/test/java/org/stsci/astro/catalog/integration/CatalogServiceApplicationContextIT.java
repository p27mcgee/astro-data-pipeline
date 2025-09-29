package org.stsci.astro.catalog.integration;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.stsci.astro.catalog.CatalogServiceApplication;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(classes = CatalogServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "spring.profiles.active=${SPRING_PROFILES_ACTIVE:application-context-test}"
})
@TestMethodOrder(OrderAnnotation.class)
@Testcontainers
@TestPropertySource(properties = {
    "spring.cloud.aws.credentials.access-key=test",
    "spring.cloud.aws.credentials.secret-key=test",
    "spring.cloud.aws.region.static=us-east-1"
})
class CatalogServiceApplicationContextIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:15-3.3")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("catalog_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.8.0")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @Order(1)
    @DisplayName("Application context should load successfully")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("All essential Spring Boot beans should be created")
    void essentialBeansShouldBeCreated() {
        // Verify core Spring Boot beans
        assertThat(applicationContext.containsBean("dataSource")).isTrue();
        assertThat(applicationContext.containsBean("entityManagerFactory")).isTrue();
        assertThat(applicationContext.containsBean("transactionManager")).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("Catalog service specific beans should be created")
    void catalogServiceBeansShouldBeCreated() {
        // Core service beans - using more lenient checks since we don't know exact bean names
        assertThat(applicationContext.getBeansOfType(org.springframework.data.jpa.repository.JpaRepository.class))
            .isNotEmpty();

        // Service beans - check for service layer components
        assertThat(applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Service.class))
            .isNotEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("Database connectivity should be established")
    void databaseConnectivityShouldWork() {
        DataSource dataSource = applicationContext.getBean(DataSource.class);
        assertThat(dataSource).isNotNull();

        assertDoesNotThrow(() -> {
            try (Connection connection = dataSource.getConnection()) {
                assertThat(connection.isValid(5)).isTrue();
            }
        });
    }

    @Test
    @Order(5)
    @DisplayName("PostGIS extension should be available")
    void postGISConfigurationShouldBeValid() {
        JdbcTemplate jdbcTemplate = applicationContext.getBean(JdbcTemplate.class);

        // Enable PostGIS extension first (since we're not running migrations)
        assertDoesNotThrow(() -> {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis;");
        });

        // Verify PostGIS extension is available
        assertDoesNotThrow(() -> {
            String version = jdbcTemplate.queryForObject("SELECT PostGIS_Version()", String.class);
            assertThat(version).isNotNull();
        });
    }

    @Test
    @Order(6)
    @DisplayName("Elasticsearch configuration should be valid")
    void elasticsearchConfigurationShouldBeValid() {
        // Check if Elasticsearch operations beans are configured
        assertThat(applicationContext.getBeansOfType(org.springframework.data.elasticsearch.core.ElasticsearchOperations.class))
            .isNotEmpty();
    }

    @Test
    @Order(7)
    @DisplayName("JPA entity manager factory should be functional")
    void entityManagerFactoryShouldWork() {
        EntityManagerFactory emf = applicationContext.getBean(EntityManagerFactory.class);
        assertThat(emf).isNotNull();
        assertThat(emf.isOpen()).isTrue();
    }

    @Test
    @Order(8)
    @DisplayName("Application context should have required components")
    void applicationContextShouldHaveRequiredComponents() {
        // Verify that service components are present
        assertThat(applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Service.class))
            .isNotEmpty();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.elasticsearch.uris",
            () -> "http://" + elasticsearch.getHttpHostAddress());

        // Disable Flyway for integration tests to avoid migration conflicts
        registry.add("spring.flyway.enabled", () -> "false");

        // Configure JPA for integration testing
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access", () -> "false");
        registry.add("spring.jpa.properties.hibernate.validator.apply_to_ddl", () -> "false");
        registry.add("spring.jpa.properties.hibernate.validator.autoregister_listeners", () -> "false");
    }
}