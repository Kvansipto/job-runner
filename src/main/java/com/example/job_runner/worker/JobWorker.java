package com.example.job_runner.worker;

import com.example.job_runner.jobs.TestJob;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Service
public class JobWorker {
    private static final int MAX_RETRIES = 3;

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TestJob testJob;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    public JobWorker(StringRedisTemplate redisTemplate,
                     KafkaTemplate<String, String> kafkaTemplate,
                     ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.testJob = new TestJob();
    }

    @KafkaListener(topics = "job-queue", groupId = "job-workers")
    public void processJob(String message) {
        executor.submit(() -> handleJob(message));
    }

    private void handleJob(String message) {
        String jobId = null;
        try {
            Map<String, Object> jobData = objectMapper.readValue(message, new TypeReference<>() {
            });
            jobId = (String) jobData.get("jobId");
            int min = (int) jobData.get("min");
            int max = (int) jobData.get("max");
            int count = (int) jobData.get("count");

            String jobKey = "job:" + jobId;

            String status = (String) redisTemplate.opsForHash().get(jobKey, "status");
            if ("COMPLETED".equals(status)) {
                System.out.println("Task " + jobId + " has already been completed");
                return;
            }

            if (!"RUNNING".equals(status)) {
                redisTemplate.opsForHash().put(jobKey, "status", "RUNNING");
            }

            String progressStr = (String) redisTemplate.opsForHash().get(jobKey, "progress");
            int progress = progressStr != null ? Integer.parseInt(progressStr) : 0;

            String resultStr = (String) redisTemplate.opsForHash().get(jobKey, "result");
            List<Integer> numbers = resultStr != null ? objectMapper.readValue(resultStr, new TypeReference<>() {
            }) : new ArrayList<>();

            Stream<Integer> stream = testJob.run(min, max, count);
            Instant lastUpdate = Instant.now();

            for (Integer number : (Iterable<Integer>) stream::iterator) {
                numbers.add(number);
                progress++;

                if (Instant.now().isAfter(lastUpdate.plusSeconds(5))) {
                    updateRedis(jobKey, progress, numbers);
                    lastUpdate = Instant.now();
                }
            }

            updateRedis(jobKey, progress, numbers);
            redisTemplate.opsForHash().put(jobKey, "status", "COMPLETED");
            redisTemplate.delete("lock:job:" + min + "_" + max + "_" + count);

        } catch (Exception e) {
            String retryCountStr = (String) redisTemplate.opsForHash().get("job:" + jobId, "retries");
            int retryCount = retryCountStr != null ? Integer.parseInt(retryCountStr) : 0;

            if (retryCount < MAX_RETRIES) {
                System.out.println("Rerunning job " + jobId + " (attempt " + (retryCount + 1) + ")");
                redisTemplate.opsForHash().put("job:" + jobId, "retries", String.valueOf(retryCount + 1));
                kafkaTemplate.send("job-queue", jobId, message);
            } else {
                System.err.println("Max amount of attempts has reached. Job " + jobId + " has FAILED status");
                redisTemplate.opsForHash().put("job:" + jobId, "status", "FAILED");
            }
        }
    }

    private void updateRedis(String jobKey, int progress, List<Integer> numbers) {
        try {
            redisTemplate.opsForHash().put(jobKey, "progress", String.valueOf(progress));
            redisTemplate.opsForHash().put(jobKey, "result", objectMapper.writeValueAsString(numbers));
        } catch (Exception e) {
            System.err.println("Error while updating job status in Redis: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
