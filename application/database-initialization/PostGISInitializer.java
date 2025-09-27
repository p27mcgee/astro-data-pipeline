package com.stsci.astro.pipeline.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PostGIS Extension Initializer for Astronomical Data Pipeline
 *
 * BACKGROUND:
 * The astronomical data processing pipeline requires PostGIS spatial extensions
 * for efficient coordinate system transformations, cone searches, and spatial
 * indexing of astronomical objects.
 *
 * INFRASTRUCTURE DECISION:
 * After evaluating complex infrastructure approaches (Lambda functions, ECS Fargate),
 * we chose application-level installation following AWS RDS PostgreSQL best practices.
 * This provides simplicity, reliability, and proper separation of concerns.
 *
 * IMPLEMENTATION:
 * This component automatically installs PostGIS extensions during application startup.
 * The installation is idempotent and safe to run multiple times.
 *
 * EXTENSIONS INSTALLED:
 * - postgis: Core spatial functionality for geometric data types and operations
 * - postgis_topology: Advanced topological operations for complex spatial relationships
 *
 * REFERENCE:
 * https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.PostGIS.html
 *
 * @author STScI Astronomical Data Pipeline Team
 * @since 2025-09-26
 */
@Component
public class PostGISInitializer {

    private static final Logger logger = LoggerFactory.getLogger(PostGISInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public PostGISInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Initialize PostGIS extensions after application startup.
     *
     * This method runs automatically when the Spring Boot application is fully started
     * and ready to serve requests. PostGIS installation happens during this phase to
     * ensure database connectivity is established and connection pools are initialized.
     *
     * TIMING: Executes after ApplicationReadyEvent to ensure:
     * - Database connection pools are initialized
     * - All Spring beans are created and ready
     * - Application health checks can verify PostGIS availability
     *
     * ERROR HANDLING: Any failure will prevent application startup, ensuring
     * the system fails fast if PostGIS cannot be installed.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializePostGIS() {
        logger.info("Initializing PostGIS extensions for astronomical data processing...");

        try {
            // Install core PostGIS extension for spatial data types and functions
            logger.debug("Installing PostGIS core extension...");
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis;");

            // Install PostGIS topology extension for advanced spatial relationships
            logger.debug("Installing PostGIS topology extension...");
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis_topology;");

            // Verify installation by checking PostGIS version
            String postgisVersion = jdbcTemplate.queryForObject(
                "SELECT PostGIS_version();",
                String.class
            );

            logger.info("PostGIS extensions installed successfully. Version: {}", postgisVersion);

            // Log available spatial reference systems for astronomical coordinates
            Integer sridCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spatial_ref_sys WHERE auth_name = 'EPSG';",
                Integer.class
            );

            logger.info("Spatial reference systems available: {} EPSG codes for coordinate transformations", sridCount);

        } catch (Exception e) {
            logger.error("Failed to initialize PostGIS extensions. Application startup will fail.", e);
            throw new RuntimeException("PostGIS initialization failed", e);
        }
    }
}