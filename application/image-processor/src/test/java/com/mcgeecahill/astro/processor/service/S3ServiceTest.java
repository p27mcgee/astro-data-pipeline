package com.mcgeecahill.astro.processor.service;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for S3Service
 * Critical tests to prevent data loss in storage operations
 */
@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Template s3Template;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Resource s3Resource;

    @InjectMocks
    private S3Service s3Service;

    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_KEY = "test/object.fits";
    private static final byte[] TEST_DATA = "test astronomical data".getBytes();

    @BeforeEach
    void setUp() {
        // Common setup for tests
    }

    // ========== Data Storage Tests ==========

    @Test
    void storeData_ValidInput_ShouldStoreSuccessfully() {
        // Given
        when(s3Template.store(eq(TEST_BUCKET), eq(TEST_KEY), any(InputStream.class))).thenReturn(s3Resource);

        // When
        String result = s3Service.storeData(TEST_BUCKET, TEST_KEY, TEST_DATA);

        // Then
        assertNotNull(result);
        assertEquals("s3://" + TEST_BUCKET + "/" + TEST_KEY, result);
        verify(s3Template).store(eq(TEST_BUCKET), eq(TEST_KEY), any(InputStream.class));
    }

    @Test
    void storeData_EmptyData_ShouldHandleGracefully() {
        // Given
        byte[] emptyData = new byte[0];
        when(s3Template.store(eq(TEST_BUCKET), eq(TEST_KEY), any(InputStream.class))).thenReturn(s3Resource);

        // When
        String result = s3Service.storeData(TEST_BUCKET, TEST_KEY, emptyData);

        // Then
        assertNotNull(result);
        assertEquals("s3://" + TEST_BUCKET + "/" + TEST_KEY, result);
        verify(s3Template).store(eq(TEST_BUCKET), eq(TEST_KEY), any(InputStream.class));
    }

    @Test
    void storeData_S3Exception_ShouldThrowRuntimeException() {
        // Given
        RuntimeException s3Exception = new RuntimeException("S3 storage failed");
        when(s3Template.store(eq(TEST_BUCKET), eq(TEST_KEY), any(InputStream.class))).thenThrow(s3Exception);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Service.storeData(TEST_BUCKET, TEST_KEY, TEST_DATA);
        });

        assertEquals("Failed to store data in S3", exception.getMessage());
        assertEquals(s3Exception, exception.getCause());
    }

    // ========== Data Retrieval Tests ==========

    @Test
    void retrieveData_ValidKey_ShouldReturnData() throws Exception {
        // Given
        InputStream testInputStream = new ByteArrayInputStream(TEST_DATA);
        when(s3Template.download(TEST_BUCKET, TEST_KEY)).thenReturn(s3Resource);
        when(s3Resource.getInputStream()).thenReturn(testInputStream);

        // When
        byte[] result = s3Service.retrieveData(TEST_BUCKET, TEST_KEY);

        // Then
        assertNotNull(result);
        assertArrayEquals(TEST_DATA, result);
        verify(s3Template).download(TEST_BUCKET, TEST_KEY);
    }

    @Test
    void retrieveData_NonExistentKey_ShouldThrowException() {
        // Given
        RuntimeException s3Exception = new RuntimeException("Object not found");
        when(s3Template.download(TEST_BUCKET, "non-existent-key")).thenThrow(s3Exception);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Service.retrieveData(TEST_BUCKET, "non-existent-key");
        });

        assertEquals("Failed to retrieve data from S3", exception.getMessage());
        assertEquals(s3Exception, exception.getCause());
    }

    // ========== Object Existence Tests ==========

    @Test
    void objectExists_ExistingObject_ShouldReturnTrue() {
        // Given
        HeadObjectResponse headObjectResponse = HeadObjectResponse.builder().build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(headObjectResponse);

        // When
        boolean exists = s3Service.objectExists(TEST_BUCKET, TEST_KEY);

        // Then
        assertTrue(exists);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void objectExists_NonExistentObject_ShouldReturnFalse() {
        // Given
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        // When
        boolean exists = s3Service.objectExists(TEST_BUCKET, TEST_KEY);

        // Then
        assertFalse(exists);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void objectExists_S3Exception_ShouldReturnFalse() {
        // Given
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 error"));

        // When
        boolean exists = s3Service.objectExists(TEST_BUCKET, TEST_KEY);

        // Then
        assertFalse(exists);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    // ========== Object Listing Tests ==========

    @Test
    void listObjects_ValidPrefix_ShouldReturnObjectKeys() {
        // Given
        String prefix = "fits/";
        List<S3Object> s3Objects = Arrays.asList(
                S3Object.builder().key("fits/image1.fits").build(),
                S3Object.builder().key("fits/image2.fits").build(),
                S3Object.builder().key("fits/calibration/dark.fits").build()
        );

        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(s3Objects)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        // When
        List<String> objectKeys = s3Service.listObjects(TEST_BUCKET, prefix);

        // Then
        assertNotNull(objectKeys);
        assertEquals(3, objectKeys.size());
        assertTrue(objectKeys.contains("fits/image1.fits"));
        assertTrue(objectKeys.contains("fits/image2.fits"));
        assertTrue(objectKeys.contains("fits/calibration/dark.fits"));
        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    void listObjects_EmptyResult_ShouldReturnEmptyList() {
        // Given
        ListObjectsV2Response emptyResponse = ListObjectsV2Response.builder()
                .contents(List.of())
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(emptyResponse);

        // When
        List<String> objectKeys = s3Service.listObjects(TEST_BUCKET, "empty-prefix/");

        // Then
        assertNotNull(objectKeys);
        assertTrue(objectKeys.isEmpty());
    }

    // ========== Object Deletion Tests ==========

    @Test
    void deleteObject_ExistingObject_ShouldDeleteSuccessfully() {
        // Given
        DeleteObjectResponse deleteResponse = DeleteObjectResponse.builder().build();
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(deleteResponse);

        // When & Then
        assertDoesNotThrow(() -> {
            s3Service.deleteObject(TEST_BUCKET, TEST_KEY);
        });

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_S3Exception_ShouldThrowRuntimeException() {
        // Given
        RuntimeException s3Exception = new RuntimeException("Delete failed");
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(s3Exception);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Service.deleteObject(TEST_BUCKET, TEST_KEY);
        });

        assertEquals("Failed to delete object", exception.getMessage());
        assertEquals(s3Exception, exception.getCause());
    }

    // ========== Object Copy Tests ==========

    @Test
    void copyObject_ValidParameters_ShouldCopySuccessfully() {
        // Given
        String sourceBucket = "source-bucket";
        String sourceKey = "source/file.fits";
        String destBucket = "dest-bucket";
        String destKey = "dest/file.fits";

        CopyObjectResponse copyResponse = CopyObjectResponse.builder().build();
        when(s3Client.copyObject(any(CopyObjectRequest.class))).thenReturn(copyResponse);

        // When & Then
        assertDoesNotThrow(() -> {
            s3Service.copyObject(sourceBucket, sourceKey, destBucket, destKey);
        });

        verify(s3Client).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    void copyObject_S3Exception_ShouldThrowRuntimeException() {
        // Given
        RuntimeException s3Exception = new RuntimeException("Copy failed");
        when(s3Client.copyObject(any(CopyObjectRequest.class))).thenThrow(s3Exception);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Service.copyObject("src-bucket", "src-key", "dst-bucket", "dst-key");
        });

        assertEquals("Failed to copy object", exception.getMessage());
        assertEquals(s3Exception, exception.getCause());
    }

    // ========== Pre-signed URL Tests ==========

    @Test
    void generatePresignedUrl_ValidParameters_ShouldReturnUrl() throws Exception {
        // Given
        URL mockUrl = new URL("https://test-bucket.s3.amazonaws.com/test/object.fits?presigned-params");
        when(s3Template.createSignedGetURL(eq(TEST_BUCKET), eq(TEST_KEY), any(java.time.Duration.class)))
                .thenReturn(mockUrl);

        // When
        String presignedUrl = s3Service.generatePresignedUrl(TEST_BUCKET, TEST_KEY, 60);

        // Then
        assertNotNull(presignedUrl);
        assertEquals(mockUrl.toString(), presignedUrl);
        verify(s3Template).createSignedGetURL(eq(TEST_BUCKET), eq(TEST_KEY), any(java.time.Duration.class));
    }

    @Test
    void generatePresignedUrl_S3Exception_ShouldThrowRuntimeException() {
        // Given
        RuntimeException s3Exception = new RuntimeException("URL generation failed");
        when(s3Template.createSignedGetURL(eq(TEST_BUCKET), eq(TEST_KEY), any(java.time.Duration.class)))
                .thenThrow(s3Exception);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Service.generatePresignedUrl(TEST_BUCKET, TEST_KEY, 60);
        });

        assertEquals("Failed to generate presigned URL", exception.getMessage());
        assertEquals(s3Exception, exception.getCause());
    }

    // ========== S3 Path Handling Tests ==========

    @Test
    void downloadFile_ValidS3Path_ShouldParseAndRetrieve() throws Exception {
        // Given
        String s3Path = "s3://my-bucket/path/to/file.fits";
        InputStream testInputStream = new ByteArrayInputStream(TEST_DATA);
        when(s3Template.download("my-bucket", "path/to/file.fits")).thenReturn(s3Resource);
        when(s3Resource.getInputStream()).thenReturn(testInputStream);

        // When
        byte[] result = s3Service.downloadFile(s3Path);

        // Then
        assertNotNull(result);
        assertArrayEquals(TEST_DATA, result);
        verify(s3Template).download("my-bucket", "path/to/file.fits");
    }

    @Test
    void downloadFile_InvalidS3Path_ShouldThrowException() {
        // Given
        String invalidPath = "https://example.com/file.fits";

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Service.downloadFile(invalidPath);
        });

        assertEquals("Failed to download file from S3", exception.getMessage());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void uploadFile_ValidS3Path_ShouldParseAndStore() {
        // Given
        String s3Path = "s3://my-bucket/upload/file.fits";
        when(s3Template.store(eq("my-bucket"), eq("upload/file.fits"), any(InputStream.class))).thenReturn(s3Resource);

        // When
        String result = s3Service.uploadFile(s3Path, TEST_DATA);

        // Then
        assertNotNull(result);
        assertEquals("s3://my-bucket/upload/file.fits", result);
        verify(s3Template).store(eq("my-bucket"), eq("upload/file.fits"), any(InputStream.class));
    }

    @Test
    void uploadFile_InvalidS3Path_ShouldThrowException() {
        // Given
        String invalidPath = "ftp://example.com/file.fits";

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Service.uploadFile(invalidPath, TEST_DATA);
        });

        assertEquals("Failed to upload file to S3", exception.getMessage());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    // ========== Edge Cases and Error Handling ==========

    @Test
    void storeData_NullBucket_ShouldThrowException() {
        // Given - Mock S3Template to throw exception for null bucket
        when(s3Template.store(eq(null), eq(TEST_KEY), any(InputStream.class)))
                .thenThrow(new IllegalArgumentException("Bucket name cannot be null"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            s3Service.storeData(null, TEST_KEY, TEST_DATA);
        });
    }

    @Test
    void storeData_NullKey_ShouldThrowException() {
        // Given - Mock S3Template to throw exception for null key
        when(s3Template.store(eq(TEST_BUCKET), eq(null), any(InputStream.class)))
                .thenThrow(new IllegalArgumentException("Object key cannot be null"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            s3Service.storeData(TEST_BUCKET, null, TEST_DATA);
        });
    }

    @Test
    void storeData_NullData_ShouldThrowException() {
        // When & Then
        assertThrows(Exception.class, () -> {
            s3Service.storeData(TEST_BUCKET, TEST_KEY, null);
        });
    }

    @Test
    void retrieveData_NullBucket_ShouldThrowException() {
        // When & Then
        assertThrows(Exception.class, () -> {
            s3Service.retrieveData(null, TEST_KEY);
        });
    }

    @Test
    void retrieveData_NullKey_ShouldThrowException() {
        // When & Then
        assertThrows(Exception.class, () -> {
            s3Service.retrieveData(TEST_BUCKET, null);
        });
    }

    // ========== Large Data Handling Tests ==========

    @Test
    void storeData_LargeData_ShouldHandleCorrectly() {
        // Given - Simulate large FITS file (10MB)
        byte[] largeData = new byte[10 * 1024 * 1024];
        Arrays.fill(largeData, (byte) 0x42); // Fill with test pattern

        when(s3Template.store(eq(TEST_BUCKET), eq("large-file.fits"), any(InputStream.class))).thenReturn(s3Resource);

        // When
        String result = s3Service.storeData(TEST_BUCKET, "large-file.fits", largeData);

        // Then
        assertNotNull(result);
        assertEquals("s3://" + TEST_BUCKET + "/large-file.fits", result);
        verify(s3Template).store(eq(TEST_BUCKET), eq("large-file.fits"), any(InputStream.class));
    }

    // ========== Data Integrity Tests ==========

    @Test
    void storeAndRetrieve_SameData_ShouldMaintainIntegrity() throws Exception {
        // Given
        when(s3Template.store(eq(TEST_BUCKET), eq(TEST_KEY), any(InputStream.class))).thenReturn(s3Resource);

        InputStream retrieveInputStream = new ByteArrayInputStream(TEST_DATA);
        when(s3Template.download(TEST_BUCKET, TEST_KEY)).thenReturn(s3Resource);
        when(s3Resource.getInputStream()).thenReturn(retrieveInputStream);

        // When
        String storeResult = s3Service.storeData(TEST_BUCKET, TEST_KEY, TEST_DATA);
        byte[] retrievedData = s3Service.retrieveData(TEST_BUCKET, TEST_KEY);

        // Then
        assertNotNull(storeResult);
        assertNotNull(retrievedData);
        assertArrayEquals(TEST_DATA, retrievedData, "Retrieved data should match stored data");
    }

    // ========== Astronomical Data Specific Tests ==========

    @Test
    void storeData_FitsFile_ShouldHandleFitsSpecificContent() {
        // Given - Simulate FITS file header content
        String fitsHeader = "SIMPLE  =                    T / file does conform to FITS standard             \n" +
                "BITPIX  =                  -32 / number of bits per data pixel                \n" +
                "NAXIS   =                    2 / number of data axes                           \n" +
                "NAXIS1  =                 1024 / length of data axis 1                        \n" +
                "NAXIS2  =                 1024 / length of data axis 2                        \n" +
                "END                                                                             ";
        byte[] fitsData = fitsHeader.getBytes();

        when(s3Template.store(eq(TEST_BUCKET), eq("telescope_image.fits"), any(InputStream.class))).thenReturn(s3Resource);

        // When
        String result = s3Service.storeData(TEST_BUCKET, "telescope_image.fits", fitsData);

        // Then
        assertNotNull(result);
        assertEquals("s3://" + TEST_BUCKET + "/telescope_image.fits", result);
        verify(s3Template).store(eq(TEST_BUCKET), eq("telescope_image.fits"), any(InputStream.class));
    }

    @Test
    void listObjects_FitsFiles_ShouldFilterCorrectly() {
        // Given
        String fitsPrefix = "observations/2024/";
        List<S3Object> s3Objects = Arrays.asList(
                S3Object.builder().key("observations/2024/m31_001.fits").build(),
                S3Object.builder().key("observations/2024/m31_002.fits").build(),
                S3Object.builder().key("observations/2024/calibration.txt").build(),
                S3Object.builder().key("observations/2024/dark_frame.fits").build()
        );

        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(s3Objects)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        // When
        List<String> objectKeys = s3Service.listObjects(TEST_BUCKET, fitsPrefix);

        // Then
        assertNotNull(objectKeys);
        assertEquals(4, objectKeys.size());

        // Count FITS files
        long fitsCount = objectKeys.stream()
                .filter(key -> key.endsWith(".fits"))
                .count();

        assertEquals(3, fitsCount, "Should have 3 FITS files in the listing");
    }
}