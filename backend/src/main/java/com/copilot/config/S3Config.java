package com.copilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Profile("aws")
public class S3Config {

    @Bean
    public S3Client s3Client() {
        // No explicit credentials provider: defaults to the SDK's standard chain, which
        // in EKS resolves to the pod's IRSA role automatically.
        return S3Client.builder().build();
    }
}
