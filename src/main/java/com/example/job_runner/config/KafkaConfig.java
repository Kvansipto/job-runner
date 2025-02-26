package com.example.job_runner.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

  @Value("${spring.kafka.topic}")
  private String topic;

  @Value("${spring.kafka.streams.replication-factor}")
  private short replicationFactor;

  @Bean
  public NewTopic jobTopic() {
    return new NewTopic(topic, 3, replicationFactor);
  }
}
