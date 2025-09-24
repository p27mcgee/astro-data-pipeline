package org.stsci.astro.processor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nom.tam.fits.*;
import nom.tam.util.Cursor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.stsci.astro.processor.config.ProcessingConfig;
import org.stsci.astro.processor.dto.FitsImageData;
import org.stsci.astro.processor.dto.ProcessingResult;
import org.stsci.astro.processor.entity.ProcessingJob;
import org.stsci.astro.processor.exception.FitsProcessingException;
import org.stsci.astro.processor.util.AstronomicalUtils;
import org.stsci.astro.processor.util.MetricsCollector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitsProcessingService {

    private final ProcessingConfig processingConfig;
    private final MetricsCollector metricsCollector;
    private final AstronomicalUtils astronomicalUtils;

    @Retryable(value = {FitsProcessingException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<ProcessingResult> processImage(ProcessingJob job, InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            ProcessingJob.ProcessingMetrics metrics = ProcessingJob.ProcessingMetrics.builder().build();
            
            try {
                log.info("Starting FITS processing for job: {}", job.getJobId());
                
                // Load FITS file
                FitsImageData imageData = loadFitsFile(inputStream, job);
                metrics.setIoReadBytes(imageData.getFileSizeBytes());
                
                // Validate FITS data
                validateFitsData(imageData);
                
                // Apply calibration steps based on job configuration
                ProcessingResult.ProcessingResultBuilder resultBuilder = ProcessingResult.builder()
                        .jobId(job.getJobId())
                        .originalImageSize(imageData.getImageData().length);
                
                float[][] processedData = imageData.getImageData();
                List<ProcessingJob.ProcessingStep> completedSteps = new ArrayList<>();
                
                // Dark frame subtraction
                if (processingConfig.getCalibration().isEnableDarkSubtraction()) {
                    processedData = applyDarkSubtraction(processedData, imageData);
                    completedSteps.add(ProcessingJob.ProcessingStep.DARK_SUBTRACTION);
                    log.debug("Applied dark subtraction for job: {}", job.getJobId());
                }
                
                // Flat field correction
                if (processingConfig.getCalibration().isEnableFlatCorrection()) {
                    processedData = applyFlatFieldCorrection(processedData, imageData);
                    completedSteps.add(ProcessingJob.ProcessingStep.FLAT_CORRECTION);
                    log.debug("Applied flat field correction for job: {}", job.getJobId());
                }
                
                // Cosmic ray detection and removal
                int cosmicRaysDetected = 0;
                if (processingConfig.getCalibration().isEnableCosmicRayRemoval()) {
                    cosmicRaysDetected = removeCosmicRays(processedData, imageData);
                    completedSteps.add(ProcessingJob.ProcessingStep.COSMIC_RAY_REMOVAL);
                    metrics.setCosmicRaysDetected(cosmicRaysDetected);
                    log.debug("Removed {} cosmic rays for job: {}", cosmicRaysDetected, job.getJobId());
                }
                
                // Calculate image quality metrics
                double qualityScore = calculateImageQuality(processedData);
                metrics.setImageQualityScore(qualityScore);
                
                // Generate output FITS file
                byte[] outputData = generateOutputFits(processedData, imageData);
                metrics.setIoWriteBytes((long) outputData.length);
                
                // Calculate processing performance metrics
                long processingTime = System.currentTimeMillis() - startTime;
                metrics.setCpuTimeMs(processingTime);
                metrics.setProcessingFps(calculateProcessingFps(imageData.getImageData().length, processingTime));
                
                // Estimate memory usage
                long estimatedMemoryMb = estimateMemoryUsage(imageData);
                metrics.setMemoryPeakMb(estimatedMemoryMb);
                
                ProcessingResult result = resultBuilder
                        .processedImageData(outputData)
                        .processedImageSize(outputData.length)
                        .completedSteps(completedSteps)
                        .metrics(metrics)
                        .qualityScore(qualityScore)
                        .cosmicRaysRemoved(cosmicRaysDetected)
                        .processingTimeMs(processingTime)
                        .build();
                
                log.info("Successfully processed FITS image for job: {} in {}ms", 
                        job.getJobId(), processingTime);
                
                // Record metrics
                metricsCollector.recordProcessingSuccess(job, processingTime);
                metricsCollector.recordImageQuality(qualityScore);
                metricsCollector.recordCosmicRayDetection(cosmicRaysDetected);
                
                return result;
                
            } catch (Exception e) {
                log.error("Failed to process FITS image for job: {}", job.getJobId(), e);
                metricsCollector.recordProcessingFailure(job, e);
                throw new FitsProcessingException("Failed to process FITS image: " + e.getMessage(), e);
            }
        });
    }

    private FitsImageData loadFitsFile(InputStream inputStream, ProcessingJob job) throws IOException, FitsException {
        log.debug("Loading FITS file for job: {}", job.getJobId());
        
        // Create temporary file for processing
        Path tempFile = Files.createTempFile("fits_processing_", ".fits");
        
        try {
            // Copy input stream to temporary file
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            // Load FITS file
            Fits fits = new Fits(tempFile.toFile());
            BasicHDU<?> primaryHDU = fits.getHDU(0);
            
            if (!(primaryHDU instanceof ImageHDU)) {
                throw new FitsProcessingException("Primary HDU is not an image HDU");
            }
            
            ImageHDU imageHDU = (ImageHDU) primaryHDU;
            Header header = imageHDU.getHeader();
            
            // Extract image data
            Object dataObject = imageHDU.getData().getData();
            float[][] imageData = convertToFloatArray(dataObject);
            
            // Extract metadata
            int width = header.getIntValue("NAXIS1");
            int height = header.getIntValue("NAXIS2");
            String instrument = header.getStringValue("INSTRUME", "UNKNOWN");
            String filter = header.getStringValue("FILTER", "UNKNOWN");
            double exposureTime = header.getDoubleValue("EXPTIME", 0.0);
            
            long fileSize = Files.size(tempFile);
            
            FitsImageData result = FitsImageData.builder()
                    .imageData(imageData)
                    .header(header)
                    .width(width)
                    .height(height)
                    .instrument(instrument)
                    .filter(filter)
                    .exposureTime(exposureTime)
                    .fileSizeBytes(fileSize)
                    .tempFilePath(tempFile)
                    .build();
            
            log.debug("Successfully loaded FITS file: {}x{} pixels, instrument: {}, filter: {}", 
                    width, height, instrument, filter);
            
            return result;
            
        } catch (Exception e) {
            // Clean up temporary file on error
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ioException) {
                log.warn("Failed to cleanup temporary file: {}", tempFile, ioException);
            }
            throw e;
        }
    }

    private float[][] convertToFloatArray(Object dataObject) {
        if (dataObject instanceof float[][]) {
            return (float[][]) dataObject;
        } else if (dataObject instanceof double[][]) {
            double[][] doubleArray = (double[][]) dataObject;
            float[][] floatArray = new float[doubleArray.length][];
            for (int i = 0; i < doubleArray.length; i++) {
                floatArray[i] = new float[doubleArray[i].length];
                for (int j = 0; j < doubleArray[i].length; j++) {
                    floatArray[i][j] = (float) doubleArray[i][j];
                }
            }
            return floatArray;
        } else if (dataObject instanceof int[][]) {
            int[][] intArray = (int[][]) dataObject;
            float[][] floatArray = new float[intArray.length][];
            for (int i = 0; i < intArray.length; i++) {
                floatArray[i] = new float[intArray[i].length];
                for (int j = 0; j < intArray[i].length; j++) {
                    floatArray[i][j] = (float) intArray[i][j];
                }
            }
            return floatArray;
        } else {
            throw new FitsProcessingException("Unsupported data type: " + dataObject.getClass());
        }
    }

    private void validateFitsData(FitsImageData imageData) {
        if (imageData.getImageData() == null) {
            throw new FitsProcessingException("Image data is null");
        }
        
        if (imageData.getWidth() <= 0 || imageData.getHeight() <= 0) {
            throw new FitsProcessingException("Invalid image dimensions: " + 
                    imageData.getWidth() + "x" + imageData.getHeight());
        }
        
        // Check image size limits
        long imageSizeBytes = (long) imageData.getWidth() * imageData.getHeight() * 4; // 4 bytes per float
        if (imageSizeBytes > processingConfig.getMemory().getMaxImageSizeBytes()) {
            throw new FitsProcessingException("Image size exceeds maximum limit: " + 
                    imageSizeBytes + " > " + processingConfig.getMemory().getMaxImageSizeBytes());
        }
        
        log.debug("FITS data validation passed: {}x{} pixels", 
                imageData.getWidth(), imageData.getHeight());
    }

    private float[][] applyDarkSubtraction(float[][] imageData, FitsImageData fitsData) {
        log.debug("Applying dark frame subtraction");
        
        // In a real implementation, this would load a master dark frame
        // For demo purposes, we'll simulate dark subtraction by subtracting a bias level
        float biasLevel = calculateBiasLevel(imageData);
        
        float[][] result = new float[imageData.length][];
        for (int i = 0; i < imageData.length; i++) {
            result[i] = new float[imageData[i].length];
            for (int j = 0; j < imageData[i].length; j++) {
                result[i][j] = Math.max(0, imageData[i][j] - biasLevel);
            }
        }
        
        return result;
    }

    private float calculateBiasLevel(float[][] imageData) {
        // Calculate bias level from overscan region or use statistical estimation
        // For demo, use a simple statistical approach
        float sum = 0;
        int count = 0;
        int height = imageData.length;
        int width = imageData[0].length;
        
        // Sample from edges of the image (typical overscan regions)
        for (int i = 0; i < Math.min(50, height); i++) {
            for (int j = 0; j < Math.min(50, width); j++) {
                sum += imageData[i][j];
                count++;
            }
        }
        
        return count > 0 ? sum / count : 0;
    }

    private float[][] applyFlatFieldCorrection(float[][] imageData, FitsImageData fitsData) {
        log.debug("Applying flat field correction");
        
        // In a real implementation, this would load a master flat frame
        // For demo purposes, we'll simulate flat field correction
        float[][] flatField = generateSimulatedFlatField(imageData.length, imageData[0].length);
        
        float[][] result = new float[imageData.length][];
        for (int i = 0; i < imageData.length; i++) {
            result[i] = new float[imageData[i].length];
            for (int j = 0; j < imageData[i].length; j++) {
                if (flatField[i][j] > 0) {
                    result[i][j] = imageData[i][j] / flatField[i][j];
                } else {
                    result[i][j] = imageData[i][j];
                }
            }
        }
        
        return result;
    }

    private float[][] generateSimulatedFlatField(int height, int width) {
        // Generate a simulated flat field with vignetting effect
        float[][] flatField = new float[height][width];
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;
        float maxDistance = (float) Math.sqrt(centerX * centerX + centerY * centerY);
        
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                float distance = (float) Math.sqrt((j - centerX) * (j - centerX) + (i - centerY) * (i - centerY));
                float vignetting = 1.0f - (distance / maxDistance) * 0.2f; // 20% vignetting at edges
                flatField[i][j] = Math.max(0.5f, vignetting); // Minimum flat value of 0.5
            }
        }
        
        return flatField;
    }

    private int removeCosmicRays(float[][] imageData, FitsImageData fitsData) {
        log.debug("Removing cosmic rays");
        
        int cosmicRaysDetected = 0;
        double threshold = processingConfig.getCalibration().getCosmicRayThreshold();
        int height = imageData.length;
        int width = imageData[0].length;
        
        // Simple cosmic ray detection using median filtering
        for (int i = 1; i < height - 1; i++) {
            for (int j = 1; j < width - 1; j++) {
                float pixelValue = imageData[i][j];
                
                // Get surrounding pixels for median calculation
                List<Float> surroundingPixels = new ArrayList<>();
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        if (di != 0 || dj != 0) { // Exclude center pixel
                            surroundingPixels.add(imageData[i + di][j + dj]);
                        }
                    }
                }
                
                surroundingPixels.sort(Float::compareTo);
                float median = surroundingPixels.get(surroundingPixels.size() / 2);
                float mad = calculateMAD(surroundingPixels, median);
                
                // Detect cosmic ray if pixel value significantly exceeds median
                if (pixelValue > median + threshold * mad && mad > 0) {
                    imageData[i][j] = median; // Replace with median value
                    cosmicRaysDetected++;
                }
            }
        }
        
        return cosmicRaysDetected;
    }

    private float calculateMAD(List<Float> values, float median) {
        List<Float> deviations = new ArrayList<>();
        for (float value : values) {
            deviations.add(Math.abs(value - median));
        }
        deviations.sort(Float::compareTo);
        return deviations.get(deviations.size() / 2);
    }

    private double calculateImageQuality(float[][] imageData) {
        // Calculate image quality metrics (simplified)
        double mean = 0;
        double variance = 0;
        int count = 0;
        
        for (float[] row : imageData) {
            for (float pixel : row) {
                mean += pixel;
                count++;
            }
        }
        mean /= count;
        
        for (float[] row : imageData) {
            for (float pixel : row) {
                variance += (pixel - mean) * (pixel - mean);
            }
        }
        variance /= count;
        
        double snr = mean / Math.sqrt(variance);
        
        // Normalize to 0-100 scale
        return Math.min(100, Math.max(0, snr * 10));
    }

    @SuppressWarnings("deprecation")  // Suppresses nom-tam-fits library deprecation warnings
    private byte[] generateOutputFits(float[][] processedData, FitsImageData originalData) throws FitsException, IOException {
        log.debug("Generating output FITS file");
        
        // Create new FITS file
        Fits outputFits = new Fits();
        
        // Create new image HDU with processed data
        @SuppressWarnings("deprecation")  // nom-tam-fits library deprecation - no alternative available
        ImageHDU imageHDU = new ImageHDU(originalData.getHeader(), new ImageData(processedData));
        
        // Update header with processing metadata
        Header header = imageHDU.getHeader();
        header.addValue("HISTORY", "Processed by Astronomical Data Pipeline", "Processing history");
        header.addValue("PROCDATE", java.time.LocalDateTime.now().toString(), "Processing date");
        header.addValue("DARKSUB", processingConfig.getCalibration().isEnableDarkSubtraction(), "Dark subtraction applied");
        header.addValue("FLATCOR", processingConfig.getCalibration().isEnableFlatCorrection(), "Flat correction applied");
        header.addValue("CRREMOV", processingConfig.getCalibration().isEnableCosmicRayRemoval(), "Cosmic ray removal applied");
        
        outputFits.addHDU(imageHDU);
        
        // Write to byte array using proper stream
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream dos = new java.io.DataOutputStream(outputStream)) {
            // Suppress deprecation warning for nom-tam-fits library - no alternative available
            var fits = outputFits;
            fits.write(dos);
        }
        
        return outputStream.toByteArray();
    }

    private double calculateProcessingFps(int totalPixels, long processingTimeMs) {
        if (processingTimeMs <= 0) return 0.0;
        return (totalPixels / 1000.0) / (processingTimeMs / 1000.0); // kilo-pixels per second
    }

    private long estimateMemoryUsage(FitsImageData imageData) {
        // Estimate memory usage based on image size and processing requirements
        long baseMemory = (long) imageData.getWidth() * imageData.getHeight() * 4; // 4 bytes per float
        long processingOverhead = baseMemory * 3; // Assume 3x overhead for processing
        return (baseMemory + processingOverhead) / (1024 * 1024); // Convert to MB
    }
}