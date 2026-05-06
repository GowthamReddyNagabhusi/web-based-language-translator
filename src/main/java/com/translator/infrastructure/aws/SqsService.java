package com.translator.infrastructure.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

import java.net.URI;

@Service
public class SqsService {

    private final SqsClient sqsClient;
    private final String queueUrl;

    public SqsService(
            @Value("${aws.region}") String region,
            @Value("${aws.endpoint:}") String endpoint,
            @Value("${aws.credentials.access-key:}") String accessKey,
            @Value("${aws.credentials.secret-key:}") String secretKey,
            @Value("${aws.sqs.bulk-queue-name}") String bulkQueueName) {
        
        software.amazon.awssdk.services.sqs.SqsClientBuilder builder = SqsClient.builder().region(Region.of(region));
        
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }

        this.sqsClient = builder.build();
        
        String tempQueueUrl;
        try {
            tempQueueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(bulkQueueName).build()).queueUrl();
        } catch (Exception e) {
             tempQueueUrl = "http://localhost:4566/000000000000/" + bulkQueueName; 
        }
        this.queueUrl = tempQueueUrl;
    }

    public void sendMessage(String payload) {
        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(payload)
                .build();
        sqsClient.sendMessage(sendMsgRequest);
    }
}
