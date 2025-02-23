package com.example.job_runner.service;

import com.example.job_runner.dto.JobStatusDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static com.example.job_runner.model.JobStatus.FAILED;
import static com.example.job_runner.model.JobStatus.PENDING;

@Slf4j
@Service
public class JobService {
    private static final Duration JOB_TTL = Duration.of(14, ChronoUnit.DAYS);

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
        log.info("Creating job with parameters: min={}, max={}, count={}", min, max, count);

        var jobId = UUID.randomUUID().toString();
        var jobKey = "job:" + jobId;

        var lockKey = "lock:job:" + min + "_" + max + "_" + count;

        var status = (String) redisTemplate.opsForHash().get(jobKey, "status");
        if (FAILED.name().equals(status)) {
            log.warn("Found a FAILED job {}. Removing and restarting.", jobId);
            redisTemplate.delete(jobKey);
            redisTemplate.delete(lockKey);
        }
        var isNew = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED");
        if (Boolean.FALSE.equals(isNew)) {
            log.error("Duplicate job attempt detected: min={}, max={}, count={}", min, max, count);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is already running!");
        }
        var jobData = Map.of(
                "status", PENDING.name(),
                "min", String.valueOf(min),
                "max", String.valueOf(max),
                "count", String.valueOf(count),
                "progress", "0",
                "result", "[]",
                "retries", "0"
        );
        redisTemplate.opsForHash().putAll(jobKey, jobData);
        redisTemplate.expire(jobKey, JOB_TTL);

        try {
            var payload = objectMapper.writeValueAsString(Map.of(
                    "jobId", jobId,
                    "min", min,
                    "max", max,
                    "count", count
            ));
            kafkaTemplate.send("job-queue", jobId, payload);
            log.info("Job {} has been successfully queued.", jobId);
        } catch (JsonProcessingException e) {
            log.error("JSON serialization error for job {}: {}", jobId, e.getMessage(), e);
            throw new RuntimeException("JSON Serialization exception: ", e);
        }
        return jobId;
    }

    public JobStatusDTO getJobStatus(String id) {
        var jobKey = "job:" + id;
        var jobData = redisTemplate.opsForHash().entries(jobKey);

        if (jobData.isEmpty()) {
            log.warn("Job {} does not exist.", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The job " + id + " doesn't exist!");
        }
        var status = jobData.get("status").toString();
        var progress = jobData.get("progress").toString();

        log.info("Job {} status retrieved: status={}, progress={}", id, status, progress);
        return new JobStatusDTO(
                status,
                progress,
                jobData.get("result").toString()
        );
    }
}
