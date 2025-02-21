package com.example.job_runner.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic jobTopic() {
        return new NewTopic("job-queue", 3, (short) 3);
    }
}
