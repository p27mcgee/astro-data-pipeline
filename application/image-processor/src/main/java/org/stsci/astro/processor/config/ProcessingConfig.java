package org.stsci.astro.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "astro.processing")
@Validated
public class ProcessingConfig {

    @Valid
    @NotNull
    private Parallel parallel = new Parallel();

    @Valid
    @NotNull
    private Memory memory = new Memory();

    @Valid
    @NotNull
    private Calibration calibration = new Calibration();

    @Valid
    @NotNull
    private S3 s3 = new S3();

    @Valid
    @NotNull
    private Monitoring monitoring = new Monitoring();

    @Data
    public static class Parallel {
        
        @Min(1)
        private int maxConcurrentJobs = 4;
        
        @Min(1)
        private int threadPoolSize = 8;
        
        @Min(1000)
        private int queueCapacity = 1000;
        
        private Duration jobTimeout = Duration.ofMinutes(30);
    }

    @Data
    public static class Memory {
        
        @Positive
        private long maxHeapSizeBytes = 2L * 1024 * 1024 * 1024; // 2GB
        
        @Positive
        private long maxImageSizeBytes = 500L * 1024 * 1024; // 500MB
        
        private boolean useMemoryMapping = true;
        
        @Min(1)
        private int bufferPoolSize = 10;
    }

    @Data
    public static class Calibration {
        
        private boolean enableDarkSubtraction = true;
        private boolean enableFlatCorrection = true;
        private boolean enableCosmicRayRemoval = true;
        private boolean enableImageStacking = true;
        
        @Positive
        private double cosmicRayThreshold = 5.0;
        
        @Positive
        private int stackingMethod = 1; // 1=median, 2=mean, 3=sigma-clipped mean
        
        @Positive
        private double sigmaClippingThreshold = 3.0;
        
        private boolean preserveOriginal = true;
        
        @NotNull
        private String outputFormat = "FITS";
        
        private boolean generateThumbnails = true;
        
        @Positive
        private int thumbnailSize = 512;
    }

    @Data
    public static class S3 {
        
        @NotNull
        private String rawDataBucket = "astro-data-pipeline-raw-data-dev";
        
        @NotNull
        private String processedDataBucket = "astro-data-pipeline-processed-data-dev";
        
        @NotNull
        private String archiveBucket = "astro-data-pipeline-archive-dev";
        
        @NotNull
        private String region = "us-east-1";
        
        @Min(1)
        private int multipartThresholdMB = 100;
        
        @Min(1)
        private int maxRetries = 3;
        
        private Duration requestTimeout = Duration.ofMinutes(5);
        
        private boolean useAccelerateEndpoint = false;
    }

    @Data
    public static class Monitoring {
        
        private boolean enableMetrics = true;
        private boolean enableTracing = true;
        private boolean enableDetailedLogging = false;
        
        @NotNull
        private String metricsNamespace = "AstroDataPipeline/ImageProcessor";
        
        private Duration metricsPublishInterval = Duration.ofMinutes(1);
        
        @Positive
        private int maxLoggedErrors = 100;
        
        private boolean enablePerformanceProfiling = false;
    }
}