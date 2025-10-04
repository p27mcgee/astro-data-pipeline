package com.mcgeecahill.astro.processor.service.storage;

import java.io.InputStream;
import java.util.List;

/**
 * Abstraction for object storage operations. Supports multiple backend implementations: AWS S3,
 * MinIO, local filesystem.
 */
public interface StorageProvider {

    /**
     * Store data in the storage backend
     *
     * @param bucketName Bucket/container name
     * @param key Object key/path
     * @param data Data to store
     * @return Storage URL or path
     */
    String storeData(String bucketName, String key, byte[] data);

    /**
     * Store data from input stream
     *
     * @param bucketName Bucket/container name
     * @param key Object key/path
     * @param inputStream Input stream containing data
     * @param contentLength Content length in bytes
     * @return Storage URL or path
     */
    String storeData(String bucketName, String key, InputStream inputStream, long contentLength);

    /**
     * Retrieve data from storage
     *
     * @param bucketName Bucket/container name
     * @param key Object key/path
     * @return Data as byte array
     */
    byte[] retrieveData(String bucketName, String key);

    /**
     * Retrieve data as input stream
     *
     * @param bucketName Bucket/container name
     * @param key Object key/path
     * @return Input stream containing data
     */
    InputStream retrieveDataAsStream(String bucketName, String key);

    /**
     * Check if object exists
     *
     * @param bucketName Bucket/container name
     * @param key Object key/path
     * @return true if object exists
     */
    boolean objectExists(String bucketName, String key);

    /**
     * List objects with prefix
     *
     * @param bucketName Bucket/container name
     * @param prefix Object key prefix
     * @return List of object keys
     */
    List<String> listObjects(String bucketName, String prefix);

    /**
     * Delete object from storage
     *
     * @param bucketName Bucket/container name
     * @param key Object key/path
     */
    void deleteObject(String bucketName, String key);

    /**
     * Copy object within storage
     *
     * @param sourceBucket Source bucket name
     * @param sourceKey Source object key
     * @param destinationBucket Destination bucket name
     * @param destinationKey Destination object key
     */
    void copyObject(
            String sourceBucket, String sourceKey, String destinationBucket, String destinationKey);

    /**
     * Generate pre-signed URL for temporary access
     *
     * @param bucketName Bucket/container name
     * @param key Object key/path
     * @param expirationMinutes URL expiration in minutes
     * @return Pre-signed URL or null if not supported
     */
    String generatePresignedUrl(String bucketName, String key, int expirationMinutes);

    /**
     * Get the storage backend type
     *
     * @return Storage type (S3, MINIO, FILESYSTEM)
     */
    StorageType getStorageType();

    enum StorageType {
        S3,
        MINIO,
        FILESYSTEM
    }
}
