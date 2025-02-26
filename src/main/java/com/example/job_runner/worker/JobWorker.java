package com.example.job_runner.worker;

import static com.example.job_runner.model.JobStatus.*;

import com.example.job_runner.jobs.TestJob;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobWorker {
  private static final int MAX_RETRIES = 3;

  private final StringRedisTemplate redisTemplate;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final TestJob testJob;
  private final ExecutorService executor = Executors.newFixedThreadPool(5);

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public JobWorker(
      StringRedisTemplate redisTemplate,
      KafkaTemplate<String, String> kafkaTemplate,
      ObjectMapper objectMapper,
      TestJob testJob) {
    this.redisTemplate = redisTemplate;
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.testJob = testJob;
  }

  @KafkaListener(topics = "job-queue", groupId = "job-workers")
  public void processJob(String message) {
    executor.submit(() -> handleJob(message));
  }

  private void handleJob(String message) {
    String jobId = null;
    String lockKey = null;
    String processingLockKey = null;
    try {
      Map<String, Object> jobData = objectMapper.readValue(message, new TypeReference<>() {});
      jobId = (String) jobData.get("jobId");
      var min = Integer.parseInt(jobData.get("min").toString());
      var max = Integer.parseInt(jobData.get("max").toString());
      var count = Integer.parseInt(jobData.get("count").toString());

      var jobKey = "job:" + jobId;
      lockKey = "lock:job:" + min + "_" + max + "_" + count;
      processingLockKey = "processing:job:" + jobId;
      redisTemplate.opsForValue().setIfAbsent(processingLockKey, "LOCKED", Duration.ofSeconds(10));

      var status = (String) redisTemplate.opsForHash().get(jobKey, "status");
      if (COMPLETED.name().equals(status)) {
        log.info("Job {} is already completed. Skipping.", jobId);
        return;
      }
      if (!RUNNING.name().equals(status)) {
        redisTemplate.opsForHash().put(jobKey, "status", RUNNING.name());
      }

      log.info("Starting job {} (min={}, max={}, count={})", jobId, min, max, count);
      var progressStr = (String) redisTemplate.opsForHash().get(jobKey, "progress");
      var progress = progressStr != null ? Integer.parseInt(progressStr) : 1;

      var resultStr = (String) redisTemplate.opsForHash().get(jobKey, "result");
      List<Integer> numbers =
          resultStr != null
              ? objectMapper.readValue(resultStr, new TypeReference<>() {})
              : new ArrayList<>();

      System.out.println("Вызов run() с progress = " + progress);
      Stream<Integer> stream = testJob.run(min, max, count, progress);
      for (var number : (Iterable<Integer>) stream::iterator) {
        numbers.add(number);
        progress++;
        updateRedis(jobKey, progress, numbers);
        redisTemplate.expire(processingLockKey, Duration.ofSeconds(1));
        Thread.sleep(100);
      }

      log.info("Job {} completed successfully.", jobId);
      updateRedis(jobKey, progress, numbers);
      redisTemplate.opsForHash().put(jobKey, "status", COMPLETED.name());
      redisTemplate.delete(processingLockKey);
      redisTemplate.delete(lockKey);

    } catch (Exception e) {
      var retryCountStr = (String) redisTemplate.opsForHash().get("job:" + jobId, "retries");
      var retryCount = retryCountStr != null ? Integer.parseInt(retryCountStr) : 0;

      if (retryCount < MAX_RETRIES) {
        log.warn("Rerunning job {} (attempt {}/{})", jobId, retryCount + 1, MAX_RETRIES);
        redisTemplate.opsForHash().put("job:" + jobId, "retries", String.valueOf(retryCount + 1));
        kafkaTemplate.send("job-queue", jobId, message);
      } else {
        log.error("Max retries reached for job {}. Marking as FAILED.", jobId);
        redisTemplate.opsForHash().put("job:" + jobId, "status", FAILED.name());
        redisTemplate.delete(processingLockKey);
        redisTemplate.delete(lockKey);
      }
    }
  }

  private void updateRedis(String jobKey, int progress, List<Integer> numbers) {
    try {
      redisTemplate.opsForHash().put(jobKey, "progress", String.valueOf(progress));
      redisTemplate.opsForHash().put(jobKey, "result", objectMapper.writeValueAsString(numbers));
      String formattedTimestamp = LocalDateTime.now().format(FORMATTER);
      redisTemplate.opsForHash().put(jobKey, "last_update", formattedTimestamp);
      log.debug("Updated job {}: progress = {}", jobKey, progress);
    } catch (Exception e) {
      log.error("Error while updating job {} status in Redis: {}", jobKey, e.getMessage(), e);
    }
  }
}
