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
import org.springframework.test.context.TestPropertySource;
import org.stsci.astro.processor.ImageProcessorApplication;
import org.stsci.astro.processor.config.TestMetricsConfig;
import org.stsci.astro.processor.service.ProcessingJobService;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(classes = {ImageProcessorApplication.class, TestMetricsConfig.class}, webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        // FIXME: Excluding metrics auto-configuration due to MetricsCollector constructor dependency issues in tests
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.actuator.metrics.MetricsAutoConfiguration"
    })
@TestPropertySource(properties = {
    "spring.profiles.active=${SPRING_PROFILES_ACTIVE:application-context-test}"
})
@TestMethodOrder(OrderAnnotation.class)
// TODO what about application-context-test.yml
@TestPropertySource(properties = {
    "spring.cloud.aws.s3.endpoint=http://localhost:4566",
    "spring.cloud.aws.credentials.access-key=test",
    "spring.cloud.aws.credentials.secret-key=test",
    "spring.cloud.aws.region.static=us-east-1",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=password",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ImageProcessorApplicationContextIT {

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

}
