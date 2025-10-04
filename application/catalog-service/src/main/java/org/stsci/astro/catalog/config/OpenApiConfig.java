package org.stsci.astro.catalog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:Catalog Service}")
    private String applicationName;

    @Value("${spring.application.version:1.0.0}")
    private String applicationVersion;

    @Bean
    public OpenAPI catalogServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("STScI Astronomical Object Catalog API")
                        .description("""
                                REST API for astronomical object catalog management and queries.
                                
                                ## API Categories
                                
                                **External APIs** (Public-facing):
                                - Catalog Search: Cone searches and coordinate-based queries
                                - Object Retrieval: Get detailed object information
                                - Cross-matching: Match observations with external catalogs
                                - Statistics: Catalog statistics and quality metrics
                                
                                **Internal APIs** (Service-to-service):
                                - Batch Ingestion: Bulk catalog object creation
                                - Quality Assessment: Internal quality analysis
                                - Photometric Calibration: Internal calibration operations
                                
                                ## Versioning
                                All endpoints are versioned using URL path: `/api/v1/...`
                                
                                Future breaking changes will increment the version: `/api/v2/...`
                                
                                ## Spatial Queries
                                This API supports PostGIS spatial queries for efficient cone searches
                                and coordinate-based filtering.
                                """)
                        .version(applicationVersion)
                        .contact(new Contact()
                                .name("STScI Data Processing Team")
                                .email("datapipeline@stsci.edu")
                                .url("https://www.stsci.edu"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8081")
                                .description("Local development server"),
                        new Server()
                                .url("https://catalog-staging.stsci.edu")
                                .description("Staging environment"),
                        new Server()
                                .url("https://catalog.stsci.edu")
                                .description("Production environment")
                ));
    }
}