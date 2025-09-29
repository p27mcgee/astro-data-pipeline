package org.stsci.astro.processor.service.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.stsci.astro.processor.service.S3Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IntermediateStorageService
 * Tests temporary file management, storage optimization, and cleanup operations
 */
@ExtendWith(MockitoExtension.class)
class IntermediateStorageServiceTest {

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private IntermediateStorageService intermediateStorageService;

    private static final String TEST_SESSION_ID = "session_12345";
    private static final String TEST_STEP_TYPE = "dark-subtraction";
    private static final String TEST_IMAGE_PATH = "raw/telescope_data.fits";
    private static final byte[] TEST_DATA = "processed image data".getBytes();
    private static final String DEFAULT_INTERMEDIATE_BUCKET = "intermediate-data";
    private static final String DEFAULT_PROCESSED_BUCKET = "processed-data";

    @BeforeEach
    void setUp() {
        // Set default bucket values using reflection
        ReflectionTestUtils.setField(intermediateStorageService, "defaultIntermediateBucket", DEFAULT_INTERMEDIATE_BUCKET);
        ReflectionTestUtils.setField(intermediateStorageService, "defaultProcessedBucket", DEFAULT_PROCESSED_BUCKET);
    }

    // ========== Store Intermediate Results Tests ==========

    @Test
    void storeIntermediateResult_ValidInput_ShouldStoreSuccessfully() {
        // Given
        when(s3Service.storeData(eq(DEFAULT_INTERMEDIATE_BUCKET), anyString(), eq(TEST_DATA)))
                .thenReturn("s3://intermediate-data/sessions/session_12345/dark-subtraction/20231201-143000/telescope_data.fits");

        // When
        String result = intermediateStorageService.storeIntermediateResult(
                TEST_SESSION_ID, TEST_STEP_TYPE, TEST_IMAGE_PATH, TEST_DATA, null, null);

        // Then
        assertNotNull(result);
        assertTrue(result.contains(DEFAULT_INTERMEDIATE_BUCKET));
        assertTrue(result.contains(TEST_SESSION_ID));
        assertTrue(result.contains(TEST_STEP_TYPE));
        assertTrue(result.contains("telescope_data.fits"));

        verify(s3Service).storeData(eq(DEFAULT_INTERMEDIATE_BUCKET), anyString(), eq(TEST_DATA));
    }

    @Test
    void storeIntermediateResult_CustomBucket_ShouldUseCustomBucket() {
        // Given
        String customBucket = "custom-intermediate";
        when(s3Service.storeData(eq(customBucket), anyString(), eq(TEST_DATA)))
                .thenReturn("s3://custom-intermediate/key");

        // When
        String result = intermediateStorageService.storeIntermediateResult(
                TEST_SESSION_ID, TEST_STEP_TYPE, TEST_IMAGE_PATH, TEST_DATA, customBucket, null);

        // Then
        assertNotNull(result);
        assertTrue(result.contains(customBucket));

        verify(s3Service).storeData(eq(customBucket), anyString(), eq(TEST_DATA));
    }

    @Test
    void storeIntermediateResult_CustomOutputPath_ShouldUseCustomPath() {
        // Given
        String customPath = "custom/output/path";
        when(s3Service.storeData(eq(DEFAULT_INTERMEDIATE_BUCKET), anyString(), eq(TEST_DATA)))
                .thenReturn("s3://intermediate-data/custom/output/path/telescope_data.fits");

        // When
        String result = intermediateStorageService.storeIntermediateResult(
                TEST_SESSION_ID, TEST_STEP_TYPE, TEST_IMAGE_PATH, TEST_DATA, null, customPath);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("custom/output/path/"));
        assertTrue(result.contains("telescope_data.fits"));

        verify(s3Service).storeData(eq(DEFAULT_INTERMEDIATE_BUCKET),
                argThat(key -> key.startsWith("custom/output/path/")), eq(TEST_DATA));
    }

    @Test
    void storeIntermediateResult_S3Exception_ShouldThrowRuntimeException() {
        // Given
        when(s3Service.storeData(anyString(), anyString(), any(byte[].class)))
                .thenThrow(new RuntimeException("S3 storage failed"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            intermediateStorageService.storeIntermediateResult(
                    TEST_SESSION_ID, TEST_STEP_TYPE, TEST_IMAGE_PATH, TEST_DATA, null, null);
        });

        assertEquals("Failed to store intermediate result", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void storeIntermediateResult_MissingFitsExtension_ShouldAddExtension() {
        // Given
        String pathWithoutExtension = "raw/telescope_data";
        when(s3Service.storeData(eq(DEFAULT_INTERMEDIATE_BUCKET), anyString(), eq(TEST_DATA)))
                .thenReturn("s3://intermediate-data/key");

        // When
        intermediateStorageService.storeIntermediateResult(
                TEST_SESSION_ID, TEST_STEP_TYPE, pathWithoutExtension, TEST_DATA, null, null);

        // Then
        verify(s3Service).storeData(eq(DEFAULT_INTERMEDIATE_BUCKET),
                argThat(key -> key.endsWith("telescope_data.fits")), eq(TEST_DATA));
    }

    // ========== Move Final Results Tests ==========

    @Test
    void moveFinalResult_ValidPath_ShouldMoveSuccessfully() {
        // Given
        String intermediatePath = "intermediate-data/sessions/session_12345/final/result.fits";
        String finalBucket = "final-results";
        String finalPath = "processed/final_result.fits";

        when(s3Service.retrieveData("intermediate-data", "sessions/session_12345/final/result.fits"))
                .thenReturn(TEST_DATA);
        when(s3Service.storeData(finalBucket, finalPath, TEST_DATA))
                .thenReturn("s3://final-results/processed/final_result.fits");

        // When
        String result = intermediateStorageService.moveFinalResult(intermediatePath, finalBucket, finalPath);

        // Then
        assertNotNull(result);
        assertEquals("final-results/processed/final_result.fits", result);

        verify(s3Service).retrieveData("intermediate-data", "sessions/session_12345/final/result.fits");
        verify(s3Service).storeData(finalBucket, finalPath, TEST_DATA);
    }

    @Test
    void moveFinalResult_DefaultBucket_ShouldUseDefaultProcessedBucket() {
        // Given
        String intermediatePath = "intermediate-data/sessions/session_12345/result.fits";

        when(s3Service.retrieveData("intermediate-data", "sessions/session_12345/result.fits"))
                .thenReturn(TEST_DATA);
        when(s3Service.storeData(eq(DEFAULT_PROCESSED_BUCKET), anyString(), eq(TEST_DATA)))
                .thenReturn("s3://processed-data/result.fits");

        // When
        String result = intermediateStorageService.moveFinalResult(intermediatePath, null, null);

        // Then
        assertNotNull(result);
        assertTrue(result.contains(DEFAULT_PROCESSED_BUCKET));

        verify(s3Service).storeData(eq(DEFAULT_PROCESSED_BUCKET), anyString(), eq(TEST_DATA));
    }

    @Test
    void moveFinalResult_DirectoryPath_ShouldPreserveFilename() {
        // Given
        String intermediatePath = "intermediate-data/sessions/session_12345/telescope_data.fits";
        String finalPath = "output/directory/";

        when(s3Service.retrieveData("intermediate-data", "sessions/session_12345/telescope_data.fits"))
                .thenReturn(TEST_DATA);
        when(s3Service.storeData(eq(DEFAULT_PROCESSED_BUCKET), anyString(), eq(TEST_DATA)))
                .thenReturn("s3://processed-data/output/directory/telescope_data.fits");

        // When
        String result = intermediateStorageService.moveFinalResult(intermediatePath, null, finalPath);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("output/directory/telescope_data.fits"));

        verify(s3Service).storeData(eq(DEFAULT_PROCESSED_BUCKET),
                eq("output/directory/telescope_data.fits"), eq(TEST_DATA));
    }

    @Test
    void moveFinalResult_RetrieveException_ShouldThrowRuntimeException() {
        // Given
        String intermediatePath = "intermediate-data/sessions/session_12345/result.fits";
        when(s3Service.retrieveData(anyString(), anyString()))
                .thenThrow(new RuntimeException("Failed to retrieve data"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            intermediateStorageService.moveFinalResult(intermediatePath, null, null);
        });

        assertEquals("Failed to move final result", exception.getMessage());
    }

    // ========== Cleanup Tests ==========

    @Test
    void cleanupIntermediateFiles_ValidFiles_ShouldDeleteAll() {
        // Given
        List<String> filesToDelete = Arrays.asList(
                "intermediate-data/sessions/session_12345/step1/file1.fits",
                "intermediate-data/sessions/session_12345/step2/file2.fits"
        );

        doNothing().when(s3Service).deleteObject(anyString(), anyString());

        // When
        intermediateStorageService.cleanupIntermediateFiles(filesToDelete);

        // Then
        verify(s3Service).deleteObject("intermediate-data", "sessions/session_12345/step1/file1.fits");
        verify(s3Service).deleteObject("intermediate-data", "sessions/session_12345/step2/file2.fits");
    }

    @Test
    void cleanupIntermediateFiles_EmptyList_ShouldDoNothing() {
        // When
        intermediateStorageService.cleanupIntermediateFiles(Collections.emptyList());

        // Then
        verify(s3Service, never()).deleteObject(anyString(), anyString());
    }

    @Test
    void cleanupIntermediateFiles_NullList_ShouldDoNothing() {
        // When
        intermediateStorageService.cleanupIntermediateFiles(null);

        // Then
        verify(s3Service, never()).deleteObject(anyString(), anyString());
    }

    @Test
    void cleanupIntermediateFiles_SomeDeletesFail_ShouldContinueWithOthers() {
        // Given
        List<String> filesToDelete = Arrays.asList(
                "intermediate-data/sessions/session_12345/file1.fits",
                "intermediate-data/sessions/session_12345/file2.fits",
                "intermediate-data/sessions/session_12345/file3.fits"
        );

        doNothing().when(s3Service).deleteObject("intermediate-data", "sessions/session_12345/file1.fits");
        doThrow(new RuntimeException("Delete failed")).when(s3Service)
                .deleteObject("intermediate-data", "sessions/session_12345/file2.fits");
        doNothing().when(s3Service).deleteObject("intermediate-data", "sessions/session_12345/file3.fits");

        // When
        assertDoesNotThrow(() -> {
            intermediateStorageService.cleanupIntermediateFiles(filesToDelete);
        });

        // Then - should attempt to delete all files despite one failure
        verify(s3Service, times(3)).deleteObject(anyString(), anyString());
    }

    // ========== Session File Listing Tests ==========

    @Test
    void listIntermediateResults_ValidSession_ShouldReturnFileList() {
        // Given
        List<String> mockS3Objects = Arrays.asList(
                "sessions/session_12345/dark-subtraction/20231201-143000/telescope_data.fits",
                "sessions/session_12345/flat-correction/20231201-143500/telescope_data.fits",
                "sessions/session_12345/final/20231201-144000/telescope_data.fits"
        );

        when(s3Service.listObjects(DEFAULT_INTERMEDIATE_BUCKET, "sessions/session_12345/"))
                .thenReturn(mockS3Objects);

        // When
        List<IntermediateStorageService.IntermediateFileInfo> results =
                intermediateStorageService.listIntermediateResults(TEST_SESSION_ID);

        // Then
        assertNotNull(results);
        assertEquals(3, results.size());

        // Verify file info parsing
        IntermediateStorageService.IntermediateFileInfo firstFile = results.get(0);
        assertNotNull(firstFile);
        assertEquals(TEST_SESSION_ID, firstFile.getSessionId());
        assertEquals("dark-subtraction", firstFile.getStepType());
        assertTrue(firstFile.getFilename().endsWith(".fits"));

        // Verify final result identification
        boolean hasFinalResult = results.stream().anyMatch(f -> f.isFinalResult());
        assertTrue(hasFinalResult, "Should identify final result file");

        verify(s3Service).listObjects(DEFAULT_INTERMEDIATE_BUCKET, "sessions/session_12345/");
    }

    @Test
    void listIntermediateResults_NoFiles_ShouldReturnEmptyList() {
        // Given
        when(s3Service.listObjects(anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        // When
        List<IntermediateStorageService.IntermediateFileInfo> results =
                intermediateStorageService.listIntermediateResults(TEST_SESSION_ID);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void listIntermediateResults_S3Exception_ShouldThrowRuntimeException() {
        // Given
        when(s3Service.listObjects(anyString(), anyString()))
                .thenThrow(new RuntimeException("S3 list failed"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            intermediateStorageService.listIntermediateResults(TEST_SESSION_ID);
        });

        assertEquals("Failed to list intermediate results", exception.getMessage());
    }

    // ========== Session Cleanup Tests ==========

    @Test
    void cleanupSessionFiles_KeepFinal_ShouldDeleteOnlyNonFinalFiles() {
        // Given
        List<String> mockS3Objects = Arrays.asList(
                "sessions/session_12345/dark-subtraction/20231201-143000/telescope_data.fits",
                "sessions/session_12345/final/20231201-144000/telescope_data.fits"
        );

        when(s3Service.listObjects(DEFAULT_INTERMEDIATE_BUCKET, "sessions/session_12345/"))
                .thenReturn(mockS3Objects);
        doNothing().when(s3Service).deleteObject(anyString(), anyString());

        // When
        intermediateStorageService.cleanupSessionFiles(TEST_SESSION_ID, true);

        // Then
        verify(s3Service).deleteObject(DEFAULT_INTERMEDIATE_BUCKET,
                "sessions/session_12345/dark-subtraction/20231201-143000/telescope_data.fits");
        verify(s3Service, never()).deleteObject(DEFAULT_INTERMEDIATE_BUCKET,
                "sessions/session_12345/final/20231201-144000/telescope_data.fits");
    }

    @Test
    void cleanupSessionFiles_DeleteAll_ShouldDeleteAllFiles() {
        // Given
        List<String> mockS3Objects = Arrays.asList(
                "sessions/session_12345/dark-subtraction/20231201-143000/telescope_data.fits",
                "sessions/session_12345/final/20231201-144000/telescope_data.fits"
        );

        when(s3Service.listObjects(DEFAULT_INTERMEDIATE_BUCKET, "sessions/session_12345/"))
                .thenReturn(mockS3Objects);
        doNothing().when(s3Service).deleteObject(anyString(), anyString());

        // When
        intermediateStorageService.cleanupSessionFiles(TEST_SESSION_ID, false);

        // Then
        verify(s3Service, times(2)).deleteObject(eq(DEFAULT_INTERMEDIATE_BUCKET), anyString());
    }

    @Test
    void cleanupSessionFiles_ExceptionDuringCleanup_ShouldHandleGracefully() {
        // Given
        List<String> mockS3Objects = Arrays.asList(
                "sessions/session_12345/dark-subtraction/20231201-143000/telescope_data.fits"
        );

        when(s3Service.listObjects(DEFAULT_INTERMEDIATE_BUCKET, "sessions/session_12345/"))
                .thenReturn(mockS3Objects);
        doThrow(new RuntimeException("Delete failed")).when(s3Service)
                .deleteObject(anyString(), anyString());

        // When - should not throw exception
        assertDoesNotThrow(() -> {
            intermediateStorageService.cleanupSessionFiles(TEST_SESSION_ID, false);
        });

        // Then
        verify(s3Service).deleteObject(anyString(), anyString());
    }

    // ========== File Path Extraction Tests ==========

    @Test
    void extractFilename_ValidPaths_ShouldExtractCorrectly() {
        // Test through public method that uses private extractFilename
        when(s3Service.storeData(anyString(), anyString(), any(byte[].class)))
                .thenReturn("stored");

        // Test various path formats
        intermediateStorageService.storeIntermediateResult(TEST_SESSION_ID, TEST_STEP_TYPE,
                "bucket/path/to/file.fits", TEST_DATA, null, null);
        verify(s3Service).storeData(anyString(),
                argThat(key -> key.endsWith("file.fits")), any(byte[].class));

        reset(s3Service);
        when(s3Service.storeData(anyString(), anyString(), any(byte[].class)))
                .thenReturn("stored");

        intermediateStorageService.storeIntermediateResult(TEST_SESSION_ID, TEST_STEP_TYPE,
                "simple_filename", TEST_DATA, null, null);
        verify(s3Service).storeData(anyString(),
                argThat(key -> key.endsWith("simple_filename.fits")), any(byte[].class));
    }

    // ========== Key Building Tests ==========

    @Test
    void buildIntermediateKey_StandardPath_ShouldFollowExpectedFormat() {
        // Given
        when(s3Service.storeData(anyString(), anyString(), any(byte[].class)))
                .thenReturn("stored");

        // When
        intermediateStorageService.storeIntermediateResult(
                TEST_SESSION_ID, TEST_STEP_TYPE, TEST_IMAGE_PATH, TEST_DATA, null, null);

        // Then - verify key format: sessions/{sessionId}/{stepType}/{timestamp}/{filename}
        verify(s3Service).storeData(eq(DEFAULT_INTERMEDIATE_BUCKET),
                argThat(key -> {
                    String[] parts = key.split("/");
                    return parts.length == 5 &&
                            parts[0].equals("sessions") &&
                            parts[1].equals(TEST_SESSION_ID) &&
                            parts[2].equals(TEST_STEP_TYPE) &&
                            parts[3].matches("\\d{8}-\\d{6}") && // timestamp pattern
                            parts[4].equals("telescope_data.fits");
                }),
                eq(TEST_DATA));
    }

    // ========== IntermediateFileInfo Tests ==========

    @Test
    void intermediateFileInfo_Builder_ShouldCreateCorrectObject() {
        // Given
        LocalDateTime testTimestamp = LocalDateTime.now();

        // When
        IntermediateStorageService.IntermediateFileInfo fileInfo =
                IntermediateStorageService.IntermediateFileInfo.builder()
                        .key("sessions/session_123/dark-subtraction/20231201-143000/test.fits")
                        .sessionId("session_123")
                        .stepType("dark-subtraction")
                        .filename("test.fits")
                        .timestamp(testTimestamp)
                        .fullPath("intermediate-data/sessions/session_123/dark-subtraction/20231201-143000/test.fits")
                        .finalResult(false)
                        .build();

        // Then
        assertNotNull(fileInfo);
        assertEquals("sessions/session_123/dark-subtraction/20231201-143000/test.fits", fileInfo.getKey());
        assertEquals("session_123", fileInfo.getSessionId());
        assertEquals("dark-subtraction", fileInfo.getStepType());
        assertEquals("test.fits", fileInfo.getFilename());
        assertEquals(testTimestamp, fileInfo.getTimestamp());
        assertFalse(fileInfo.isFinalResult());
    }

    // ========== Error Handling and Edge Cases ==========

    @Test
    void storeIntermediateResult_NullOrEmptyPath_ShouldUseDefaultFilename() {
        // Given
        when(s3Service.storeData(anyString(), anyString(), any(byte[].class)))
                .thenReturn("stored");

        // When - test null path
        intermediateStorageService.storeIntermediateResult(
                TEST_SESSION_ID, TEST_STEP_TYPE, null, TEST_DATA, null, null);

        // Then
        verify(s3Service).storeData(anyString(),
                argThat(key -> key.endsWith("unknown.fits")), any(byte[].class));

        // Reset mock
        reset(s3Service);
        when(s3Service.storeData(anyString(), anyString(), any(byte[].class)))
                .thenReturn("stored");

        // When - test empty path
        intermediateStorageService.storeIntermediateResult(
                TEST_SESSION_ID, TEST_STEP_TYPE, "", TEST_DATA, null, null);

        // Then
        verify(s3Service).storeData(anyString(),
                argThat(key -> key.endsWith("unknown.fits")), any(byte[].class));
    }

    @Test
    void moveFinalResult_PathWithoutFitsExtension_ShouldPreserveOriginal() {
        // Given
        String intermediatePath = "intermediate-data/sessions/session_12345/data_file";

        when(s3Service.retrieveData("intermediate-data", "sessions/session_12345/data_file"))
                .thenReturn(TEST_DATA);
        when(s3Service.storeData(eq(DEFAULT_PROCESSED_BUCKET), anyString(), eq(TEST_DATA)))
                .thenReturn("s3://processed-data/data_file");

        // When
        String result = intermediateStorageService.moveFinalResult(intermediatePath, null, "final_output");

        // Then
        assertNotNull(result);
        verify(s3Service).storeData(eq(DEFAULT_PROCESSED_BUCKET),
                eq("final_output/data_file"), eq(TEST_DATA));
    }

    @Test
    void parseIntermediateFileInfo_InvalidKeyFormat_ShouldReturnNull() {
        // This tests the private method indirectly through listIntermediateResults

        // Given - keys that don't match expected format
        List<String> invalidKeys = Arrays.asList(
                "invalid/key/format",
                "sessions/wrong_session_id/step/file.fits"
        );

        when(s3Service.listObjects(DEFAULT_INTERMEDIATE_BUCKET, "sessions/session_12345/"))
                .thenReturn(invalidKeys);

        // When
        List<IntermediateStorageService.IntermediateFileInfo> results =
                intermediateStorageService.listIntermediateResults("session_12345");

        // Then - should filter out invalid keys
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Invalid keys should be filtered out");
    }
}