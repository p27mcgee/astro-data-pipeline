package com.mcgeecahill.astro.processor.dto;

import com.mcgeecahill.astro.processor.entity.ProcessingJob;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ProcessingResult {

    private String jobId;
    private byte[] processedImageData;
    private int originalImageSize;
    private int processedImageSize;
    private List<ProcessingJob.ProcessingStep> completedSteps;
    private ProcessingJob.ProcessingMetrics metrics;
    private double qualityScore;
    private int cosmicRaysRemoved;
    private long processingTimeMs;
    private LocalDateTime completedAt;

    // Processing details
    private String outputFileName;
    private String outputPath;
    private String thumbnailPath;
    private Map<String, Object> processingParameters;
    private List<String> processingNotes;
    private List<String> warningMessages;

    // Image analysis results
    private AstronomicalObjects extractedObjects;
    private ImageStatistics imageStats;
    private QualityMetrics quality;

    @Data
    @Builder
    public static class AstronomicalObjects {
        private int totalObjectsDetected;
        private int starsDetected;
        private int galaxiesDetected;
        private int cosmicRaysDetected;
        private List<DetectedObject> objects;

        @Data
        @Builder
        public static class DetectedObject {
            private double x;
            private double y;
            private double ra;
            private double dec;
            private double magnitude;
            private double flux;
            private String objectType; // STAR, GALAXY, COSMIC_RAY, UNKNOWN
            private double confidence;
            private double fwhm; // Full Width Half Maximum for stars
        }
    }

    @Data
    @Builder
    public static class ImageStatistics {
        private double mean;
        private double median;
        private double standardDeviation;
        private double min;
        private double max;
        private double skewness;
        private double kurtosis;
        private long totalPixels;
        private long saturatedPixels;
        private double dynamicRange;
        private double signalToNoise;
    }

    @Data
    @Builder
    public static class QualityMetrics {
        private double overallScore; // 0-100
        private double noiseLevel;
        private double sharpness;
        private double uniformity;
        private double linearity;
        private double saturationLevel;
        private boolean hasOverexposure;
        private boolean hasUnderexposure;
        private String qualityRating; // EXCELLENT, GOOD, FAIR, POOR
        private List<String> qualityIssues;
    }

    public boolean isSuccessful() {
        return processedImageData != null && processedImageData.length > 0;
    }

    public double getCompressionRatio() {
        if (originalImageSize > 0 && processedImageSize > 0) {
            return (double) originalImageSize / processedImageSize;
        }
        return 1.0;
    }

    public double getProcessingSpeed() {
        if (processingTimeMs > 0 && originalImageSize > 0) {
            return (originalImageSize / 1024.0 / 1024.0) / (processingTimeMs / 1000.0); // MB per second
        }
        return 0.0;
    }

    public String getFormattedProcessingTime() {
        if (processingTimeMs < 1000) {
            return processingTimeMs + " ms";
        } else if (processingTimeMs < 60000) {
            return String.format("%.1f s", processingTimeMs / 1000.0);
        } else {
            long minutes = processingTimeMs / 60000;
            long seconds = (processingTimeMs % 60000) / 1000;
            return String.format("%d:%02d min", minutes, seconds);
        }
    }
}