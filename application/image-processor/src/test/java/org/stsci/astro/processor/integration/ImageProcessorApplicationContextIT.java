package org.stsci.astro.processor.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.stsci.astro.processor.ImageProcessorApplication;
import org.stsci.astro.processor.service.ProcessingJobService;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(classes = ImageProcessorApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("application-context-test")
@TestMethodOrder(OrderAnnotation.class)
@Testcontainers
@TestPropertySource(properties = {
    "spring.cloud.aws.s3.endpoint=http://localhost:4566",
    "spring.cloud.aws.credentials.access-key=test",
    "spring.cloud.aws.credentials.secret-key=test",
    "spring.cloud.aws.region.static=us-east-1"
})
class ImageProcessorApplicationContextIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

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
    @DisplayName("Image processing specific beans should be created")
    void imageProcessingBeansShouldBeCreated() {
        // Core processing beans
        assertThat(applicationContext.containsBean("processingJobService")).isTrue();

        // Repository beans
        assertThat(applicationContext.containsBean("processingJobRepository")).isTrue();

        // Controller beans
        assertThat(applicationContext.containsBean("processingController")).isTrue();
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
    @DisplayName("JdbcTemplate should be functional")
    void jdbcTemplateShouldWork() {
        JdbcTemplate jdbcTemplate = applicationContext.getBean(JdbcTemplate.class);
        assertThat(jdbcTemplate).isNotNull();

        assertDoesNotThrow(() -> {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        });
    }

    @Test
    @Order(6)
    @DisplayName("Processing job service should be injectable")
    void processingJobServiceShouldBeInjectable() {
        ProcessingJobService service = applicationContext.getBean(ProcessingJobService.class);
        assertThat(service).isNotNull();
    }

    @Test
    @Order(7)
    @DisplayName("Application context should have required components")
    void applicationContextShouldHaveRequiredComponents() {
        // Verify that web components are present
        assertThat(applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Controller.class))
            .isNotEmpty();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}