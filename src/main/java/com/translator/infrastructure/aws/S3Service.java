package com.translator.infrastructure.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

@Service
public class S3Service {

    private final S3Presigner s3Presigner;
    private final String exportBucket;

    public S3Service(
            @Value("${aws.region}") String region,
            @Value("${aws.endpoint:}") String endpoint,
            @Value("${aws.credentials.access-key:}") String accessKey,
            @Value("${aws.credentials.secret-key:}") String secretKey,
            @Value("${aws.s3.export-bucket}") String exportBucket) {
        
        this.exportBucket = exportBucket;
        
        S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(region));
        
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }

        this.s3Presigner = builder.build();
    }

    public String generatePresignedUrlForUpload(String objectKey) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(exportBucket)
                .key(objectKey)
                .contentType("application/json")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(60))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }
}
