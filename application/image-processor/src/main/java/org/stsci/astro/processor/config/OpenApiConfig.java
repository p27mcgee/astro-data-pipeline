package org.stsci.astro.processor.config;

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

    @Value("${spring.application.name:Image Processor Service}")
    private String applicationName;

    @Value("${spring.application.version:1.0.0}")
    private String applicationVersion;

    @Bean
    public OpenAPI imageProcessorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("STScI Astronomical Image Processing API")
                        .description("""
                                REST API for astronomical FITS image processing pipeline.
                                
                                ## API Categories
                                
                                **External APIs** (Public-facing):
                                - Job Management: Submit, monitor, and manage processing jobs
                                - Batch Processing: Submit multiple jobs for large-scale processing
                                - Metrics: Retrieve processing metrics and system health
                                
                                **Internal APIs** (Service-to-service):
                                - Granular Processing: Individual calibration steps for research workflows
                                - Custom Workflows: Execute custom processing sequences
                                - Algorithm Discovery: Query available processing algorithms
                                - Workflow Versioning: Retrieve processing algorithm versions
                                
                                ## Versioning
                                All endpoints are versioned using URL path: `/api/v1/...`
                                
                                Future breaking changes will increment the version: `/api/v2/...`
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
                                .url("http://localhost:8080")
                                .description("Local development server"),
                        new Server()
                                .url("https://api-staging.stsci.edu")
                                .description("Staging environment"),
                        new Server()
                                .url("https://api.stsci.edu")
                                .description("Production environment")
                ));
    }
}