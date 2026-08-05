package com.copilot.document.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * Production storage backend (Stage 7 deployment target). Credentials are never
 * configured here — the S3Client picks them up from the pod's IRSA-assigned IAM role
 * (see docs/03 and the Terraform in Stage 7), so there's no access key anywhere in this
 * app's config at all, matching the "no static AWS credentials" rule from the security plan.
 */
@Service
@Profile("aws")
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3;
    private final String bucket;

    public S3FileStorageService(S3Client s3, @Value("${app.storage.s3-bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public String store(String suggestedKey, byte[] content) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(suggestedKey)
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .build(),
                RequestBody.fromBytes(content)
        );
        return suggestedKey;
    }

    @Override
    public byte[] load(String storageKey) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(storageKey).build()).asByteArray();
    }

    @Override
    public void delete(String storageKey) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
    }
}
