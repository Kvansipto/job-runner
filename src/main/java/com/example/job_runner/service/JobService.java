package com.example.job_runner.service;

import com.example.job_runner.dto.JobStatusDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class JobService {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public JobService(KafkaTemplate<String, String> kafkaTemplate,
                      StringRedisTemplate redisTemplate,
                      ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String startJob(int min, int max, int count) {
        String jobId = UUID.randomUUID().toString();
        String jobKey = "job:" + jobId;

        String lockKey = "lock:job:" + min + "_" + max + "_" + count;

        String status = (String) redisTemplate.opsForHash().get(jobKey, "status");
        if ("FAILED".equals(status)) {
            System.out.println("Found the job in FAILED status. Delete old one and start the job.");
            redisTemplate.delete(jobKey);
            redisTemplate.delete(lockKey);
        }
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.of(24, ChronoUnit.HOURS));
        if (Boolean.FALSE.equals(isNew)) {
            throw new RuntimeException("Job is already running!");
        }
        Map<String, String> jobData = Map.of(
                "status", "PENDING",
                "min", String.valueOf(min),
                "max", String.valueOf(max),
                "count", String.valueOf(count),
                "progress", "0",
                "result", "[]",
                "retries", "0"
        );
        redisTemplate.opsForHash().putAll(jobKey, jobData);

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "jobId", jobId,
                    "min", min,
                    "max", max,
                    "count", count
            ));
            kafkaTemplate.send("job-queue", jobId, payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON Serialization exception: ", e);
        }
        return jobId;
    }

    public JobStatusDTO getJobStatus(String id) {
        String jobKey = "job:" + id;
        Map<Object, Object> jobData = redisTemplate.opsForHash().entries(jobKey);

        if (jobData.isEmpty()) {
            throw new RuntimeException("The job " + id + " doesn't exist!");
        }

        return new JobStatusDTO(
                jobData.get("status").toString(),
                jobData.get("progress").toString(),
                jobData.get("result").toString()
        );
    }
}
