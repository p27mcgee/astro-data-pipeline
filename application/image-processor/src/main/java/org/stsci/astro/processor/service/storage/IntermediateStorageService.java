package org.stsci.astro.processor.service.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.stsci.astro.processor.service.S3Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntermediateStorageService {

    private final S3Service s3Service;

    @Value("${astro.storage.intermediate-bucket:intermediate-data}")
    private String defaultIntermediateBucket;

    @Value("${astro.storage.processed-bucket:processed-data}")
    private String defaultProcessedBucket;

    public String storeIntermediateResult(String sessionId, String stepType, String originalImagePath,
                                          byte[] processedData, String outputBucket, String customOutputPath) {
        try {
            String bucket = outputBucket != null ? outputBucket : defaultIntermediateBucket;
            String key = buildIntermediateKey(sessionId, stepType, originalImagePath, customOutputPath);

            log.info("Storing intermediate result: bucket={}, key={}, size={} bytes",
                    bucket, key, processedData.length);

            s3Service.uploadFile(bucket, key, processedData);

            String fullPath = String.format("%s/%s", bucket, key);
            log.info("Successfully stored intermediate result at: {}", fullPath);

            return fullPath;

        } catch (Exception e) {
            log.error("Failed to store intermediate result for session: {}, step: {}",
                    sessionId, stepType, e);
            throw new RuntimeException("Failed to store intermediate result", e);
        }
    }

    public String moveFinalResult(String intermediateResultPath, String finalBucket, String finalPath) {
        try {
            log.info("Moving final result from {} to bucket: {}, path: {}",
                    intermediateResultPath, finalBucket, finalPath);

            // Parse source bucket and key
            String[] pathParts = intermediateResultPath.split("/", 2);
            String sourceBucket = pathParts[0];
            String sourceKey = pathParts[1];

            // Download from intermediate location
            byte[] data = s3Service.downloadFile(intermediateResultPath);

            // Build final key
            String finalKey = finalPath != null ? finalPath : sourceKey;
            if (!finalKey.endsWith(".fits")) {
                // Preserve original filename if custom path is a directory
                String originalFilename = extractFilename(sourceKey);
                if (finalKey.endsWith("/")) {
                    finalKey = finalKey + originalFilename;
                } else {
                    finalKey = finalKey + "/" + originalFilename;
                }
            }

            // Upload to final location
            String targetBucket = finalBucket != null ? finalBucket : defaultProcessedBucket;
            s3Service.uploadFile(targetBucket, finalKey, data);

            String finalResultPath = String.format("%s/%s", targetBucket, finalKey);
            log.info("Successfully moved final result to: {}", finalResultPath);

            return finalResultPath;

        } catch (Exception e) {
            log.error("Failed to move final result from: {}", intermediateResultPath, e);
            throw new RuntimeException("Failed to move final result", e);
        }
    }

    public void cleanupIntermediateFiles(List<String> intermediateFiles) {
        if (intermediateFiles == null || intermediateFiles.isEmpty()) {
            return;
        }

        log.info("Cleaning up {} intermediate files", intermediateFiles.size());

        for (String filePath : intermediateFiles) {
            try {
                String[] pathParts = filePath.split("/", 2);
                String bucket = pathParts[0];
                String key = pathParts[1];

                s3Service.deleteFile(bucket, key);
                log.debug("Deleted intermediate file: {}", filePath);

            } catch (Exception e) {
                log.warn("Failed to delete intermediate file: {}", filePath, e);
                // Continue with other files even if one fails
            }
        }

        log.info("Completed cleanup of intermediate files");
    }

    public List<IntermediateFileInfo> listIntermediateResults(String sessionId) {
        try {
            log.info("Listing intermediate results for session: {}", sessionId);

            String prefix = String.format("sessions/%s/", sessionId);
            List<String> s3Objects = s3Service.listObjects(defaultIntermediateBucket, prefix);

            List<IntermediateFileInfo> results = s3Objects.stream()
                    .map(key -> parseIntermediateFileInfo(key, sessionId))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(IntermediateFileInfo::getTimestamp))
                    .collect(Collectors.toList());

            log.info("Found {} intermediate results for session: {}", results.size(), sessionId);
            return results;

        } catch (Exception e) {
            log.error("Failed to list intermediate results for session: {}", sessionId, e);
            throw new RuntimeException("Failed to list intermediate results", e);
        }
    }

    public void cleanupSessionFiles(String sessionId, boolean keepFinalResult) {
        try {
            log.info("Cleaning up session files for: {}, keepFinal: {}", sessionId, keepFinalResult);

            List<IntermediateFileInfo> sessionFiles = listIntermediateResults(sessionId);

            for (IntermediateFileInfo fileInfo : sessionFiles) {
                if (keepFinalResult && fileInfo.isFinalResult()) {
                    log.debug("Keeping final result: {}", fileInfo.getKey());
                    continue;
                }

                try {
                    s3Service.deleteFile(defaultIntermediateBucket, fileInfo.getKey());
                    log.debug("Deleted session file: {}", fileInfo.getKey());
                } catch (Exception e) {
                    log.warn("Failed to delete session file: {}", fileInfo.getKey(), e);
                }
            }

            log.info("Completed cleanup for session: {}", sessionId);

        } catch (Exception e) {
            log.error("Failed to cleanup session files for: {}", sessionId, e);
        }
    }

    private String buildIntermediateKey(String sessionId, String stepType, String originalImagePath,
                                        String customOutputPath) {
        if (customOutputPath != null && !customOutputPath.trim().isEmpty()) {
            // Use custom path, ensuring it's properly formatted
            String customPath = customOutputPath.trim();
            if (!customPath.endsWith("/")) {
                customPath += "/";
            }
            return customPath + extractFilename(originalImagePath);
        }

        // Build standard intermediate key structure
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = extractFilename(originalImagePath);

        return String.format("sessions/%s/%s/%s/%s",
                sessionId, stepType, timestamp, filename);
    }

    private String extractFilename(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "unknown.fits";
        }

        // Handle both S3 paths (bucket/key) and simple file paths
        String[] parts = path.split("/");
        String filename = parts[parts.length - 1];

        // Ensure .fits extension
        if (!filename.toLowerCase().endsWith(".fits")) {
            filename += ".fits";
        }

        return filename;
    }

    private IntermediateFileInfo parseIntermediateFileInfo(String key, String sessionId) {
        try {
            // Expected format: sessions/{sessionId}/{stepType}/{timestamp}/{filename}
            String[] keyParts = key.split("/");

            if (keyParts.length < 4 || !keyParts[0].equals("sessions") || !keyParts[1].equals(sessionId)) {
                return null;
            }

            String stepType = keyParts[2];
            String timestamp = keyParts[3];
            String filename = keyParts.length > 4 ? keyParts[keyParts.length - 1] : keyParts[3];

            // Parse timestamp if possible
            LocalDateTime timestampParsed = null;
            try {
                timestampParsed = LocalDateTime.parse(timestamp,
                        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            } catch (Exception e) {
                log.debug("Could not parse timestamp from key: {}", key);
            }

            return IntermediateFileInfo.builder()
                    .key(key)
                    .sessionId(sessionId)
                    .stepType(stepType)
                    .filename(filename)
                    .timestamp(timestampParsed)
                    .fullPath(String.format("%s/%s", defaultIntermediateBucket, key))
                    .finalResult(stepType.equals("final") || key.contains("final"))
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse intermediate file info from key: {}", key, e);
            return null;
        }
    }

    public static class IntermediateFileInfo {
        private String key;
        private String sessionId;
        private String stepType;
        private String filename;
        private LocalDateTime timestamp;
        private String fullPath;
        private boolean finalResult;

        public static IntermediateFileInfoBuilder builder() {
            return new IntermediateFileInfoBuilder();
        }

        public String getKey() {
            return key;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getStepType() {
            return stepType;
        }

        public String getFilename() {
            return filename;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getFullPath() {
            return fullPath;
        }

        public boolean isFinalResult() {
            return finalResult;
        }

        public static class IntermediateFileInfoBuilder {
            private String key;
            private String sessionId;
            private String stepType;
            private String filename;
            private LocalDateTime timestamp;
            private String fullPath;
            private boolean finalResult;

            public IntermediateFileInfoBuilder key(String key) {
                this.key = key;
                return this;
            }

            public IntermediateFileInfoBuilder sessionId(String sessionId) {
                this.sessionId = sessionId;
                return this;
            }

            public IntermediateFileInfoBuilder stepType(String stepType) {
                this.stepType = stepType;
                return this;
            }

            public IntermediateFileInfoBuilder filename(String filename) {
                this.filename = filename;
                return this;
            }

            public IntermediateFileInfoBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public IntermediateFileInfoBuilder fullPath(String fullPath) {
                this.fullPath = fullPath;
                return this;
            }

            public IntermediateFileInfoBuilder finalResult(boolean finalResult) {
                this.finalResult = finalResult;
                return this;
            }

            public IntermediateFileInfo build() {
                IntermediateFileInfo info = new IntermediateFileInfo();
                info.key = this.key;
                info.sessionId = this.sessionId;
                info.stepType = this.stepType;
                info.filename = this.filename;
                info.timestamp = this.timestamp;
                info.fullPath = this.fullPath;
                info.finalResult = this.finalResult;
                return info;
            }
        }
    }
}