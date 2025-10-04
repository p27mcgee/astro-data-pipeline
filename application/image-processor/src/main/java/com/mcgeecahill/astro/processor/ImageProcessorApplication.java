package com.mcgeecahill.astro.processor;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableCaching
@EnableBatchProcessing
@ConfigurationPropertiesScan
public class ImageProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageProcessorApplication.class, args);
    }
}