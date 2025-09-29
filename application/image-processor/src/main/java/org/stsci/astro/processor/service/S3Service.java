package org.stsci.astro.processor.service;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Template s3Template;
    private final S3Client s3Client;

    /**
     * Store data in S3 bucket
     * @param bucketName S3 bucket name
     * @param key S3 object key
     * @param data Data to store
     * @return S3 object URL
     */
    public String storeData(String bucketName, String key, byte[] data) {
        try {
            log.debug("Storing data in S3: bucket={}, key={}, size={} bytes", bucketName, key, data.length);

            InputStream inputStream = new ByteArrayInputStream(data);
            s3Template.store(bucketName, key, inputStream);

            String s3Url = String.format("s3://%s/%s", bucketName, key);
            log.debug("Successfully stored data at: {}", s3Url);
            return s3Url;

        } catch (Exception e) {
            log.error("Failed to store data in S3: bucket={}, key={}", bucketName, key, e);
            throw new RuntimeException("Failed to store data in S3", e);
        }
    }

    /**
     * Retrieve data from S3
     * @param bucketName S3 bucket name
     * @param key S3 object key
     * @return Data as byte array
     */
    public byte[] retrieveData(String bucketName, String key) {
        try {
            log.debug("Retrieving data from S3: bucket={}, key={}", bucketName, key);

            InputStream inputStream = s3Template.download(bucketName, key).getInputStream();
            byte[] data = inputStream.readAllBytes();

            log.debug("Successfully retrieved {} bytes from S3", data.length);
            return data;

        } catch (Exception e) {
            log.error("Failed to retrieve data from S3: bucket={}, key={}", bucketName, key, e);
            throw new RuntimeException("Failed to retrieve data from S3", e);
        }
    }

    /**
     * Check if object exists in S3
     * @param bucketName S3 bucket name
     * @param key S3 object key
     * @return true if object exists
     */
    public boolean objectExists(String bucketName, String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking if object exists: bucket={}, key={}", bucketName, key, e);
            return false;
        }
    }

    /**
     * List objects with prefix
     * @param bucketName S3 bucket name
     * @param prefix Object key prefix
     * @return List of object keys
     */
    public List<String> listObjects(String bucketName, String prefix) {
        try {
            log.debug("Listing objects in S3: bucket={}, prefix={}", bucketName, prefix);

            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            List<String> objectKeys = listResponse.contents().stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());

            log.debug("Found {} objects with prefix: {}", objectKeys.size(), prefix);
            return objectKeys;

        } catch (Exception e) {
            log.error("Failed to list objects: bucket={}, prefix={}", bucketName, prefix, e);
            throw new RuntimeException("Failed to list objects", e);
        }
    }

    /**
     * Delete object from S3
     * @param bucketName S3 bucket name
     * @param key S3 object key
     */
    public void deleteObject(String bucketName, String key) {
        try {
            log.debug("Deleting object from S3: bucket={}, key={}", bucketName, key);

            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.debug("Successfully deleted object: {}", key);

        } catch (Exception e) {
            log.error("Failed to delete object: bucket={}, key={}", bucketName, key, e);
            throw new RuntimeException("Failed to delete object", e);
        }
    }

    /**
     * Copy object within S3
     * @param sourceBucket Source bucket name
     * @param sourceKey Source object key
     * @param destinationBucket Destination bucket name
     * @param destinationKey Destination object key
     */
    public void copyObject(String sourceBucket, String sourceKey, String destinationBucket, String destinationKey) {
        try {
            log.debug("Copying object in S3: {}:{} -> {}:{}", sourceBucket, sourceKey, destinationBucket, destinationKey);

            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(sourceBucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(destinationBucket)
                    .destinationKey(destinationKey)
                    .build();

            s3Client.copyObject(copyRequest);
            log.debug("Successfully copied object");

        } catch (Exception e) {
            log.error("Failed to copy object: {}:{} -> {}:{}", sourceBucket, sourceKey, destinationBucket, destinationKey, e);
            throw new RuntimeException("Failed to copy object", e);
        }
    }

    /**
     * Generate pre-signed URL for object access
     * @param bucketName S3 bucket name
     * @param key S3 object key
     * @param expirationMinutes URL expiration in minutes
     * @return Pre-signed URL
     */
    public String generatePresignedUrl(String bucketName, String key, int expirationMinutes) {
        try {
            log.debug("Generating presigned URL: bucket={}, key={}, expiration={}min", bucketName, key, expirationMinutes);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            String presignedUrl = s3Template.createSignedGetURL(bucketName, key, java.time.Duration.ofMinutes(expirationMinutes)).toString();

            log.debug("Generated presigned URL with expiration: {}min", expirationMinutes);
            return presignedUrl;

        } catch (Exception e) {
            log.error("Failed to generate presigned URL: bucket={}, key={}", bucketName, key, e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    /**
     * Download file from S3 using S3 path
     * @param s3Path S3 path in format s3://bucket/key
     * @return File data as byte array
     */
    public byte[] downloadFile(String s3Path) {
        try {
            log.debug("Downloading file from S3 path: {}", s3Path);

            if (!s3Path.startsWith("s3://")) {
                throw new IllegalArgumentException("S3 path must start with s3://");
            }

            // Parse S3 path
            String pathWithoutProtocol = s3Path.substring(5); // Remove "s3://"
            int firstSlash = pathWithoutProtocol.indexOf('/');
            if (firstSlash == -1) {
                throw new IllegalArgumentException("Invalid S3 path format");
            }

            String bucketName = pathWithoutProtocol.substring(0, firstSlash);
            String key = pathWithoutProtocol.substring(firstSlash + 1);

            return retrieveData(bucketName, key);

        } catch (Exception e) {
            log.error("Failed to download file from S3 path: {}", s3Path, e);
            throw new RuntimeException("Failed to download file from S3", e);
        }
    }

    /**
     * Upload file to S3 using S3 path
     * @param s3Path S3 path in format s3://bucket/key
     * @param data File data to upload
     * @return S3 URL
     */
    public String uploadFile(String s3Path, byte[] data) {
        try {
            log.debug("Uploading file to S3 path: {}", s3Path);

            if (!s3Path.startsWith("s3://")) {
                throw new IllegalArgumentException("S3 path must start with s3://");
            }

            // Parse S3 path
            String pathWithoutProtocol = s3Path.substring(5); // Remove "s3://"
            int firstSlash = pathWithoutProtocol.indexOf('/');
            if (firstSlash == -1) {
                throw new IllegalArgumentException("Invalid S3 path format");
            }

            String bucketName = pathWithoutProtocol.substring(0, firstSlash);
            String key = pathWithoutProtocol.substring(firstSlash + 1);

            return storeData(bucketName, key, data);

        } catch (Exception e) {
            log.error("Failed to upload file to S3 path: {}", s3Path, e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }
}