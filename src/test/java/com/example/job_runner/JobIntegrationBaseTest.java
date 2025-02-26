package com.example.job_runner;

import static com.example.job_runner.model.JobStatus.COMPLETED;
import static com.example.job_runner.model.JobStatus.RUNNING;
import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.*;

import com.example.job_runner.dto.JobRequestDTO;
import com.example.job_runner.dto.JobStatusDTO;
import com.example.job_runner.jobs.TestJob;
import com.example.job_runner.service.JobService;
import com.example.job_runner.worker.JobWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class JobIntegrationBaseTest {

  @Autowired private ObjectMapper objectMapper;

  @Autowired private StringRedisTemplate redisTemplate;

  @Autowired private JobWorker jobWorker;

  @Autowired private JobService jobService;

  @Container
  static final KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Container
  private static final RedisContainer REDIS_CONTAINER =
      new RedisContainer(DockerImageName.parse("redis"));

  @BeforeEach
  void cleanRedisBeforeTest() {
    redisTemplate.getConnectionFactory().getConnection().flushAll();
  }

  @Test
  void testCreateJobAndCheckStatuses() throws JsonProcessingException {
    var count = 20;
    var jobId = postCreateJob(new JobRequestDTO(1, 100, count));
    assertThat(jobId).isNotBlank();

    Set<String> observedStatuses = new ConcurrentSkipListSet<>();

    await()
        .atMost(3, SECONDS)
        .pollInterval(10, MILLISECONDS)
        .until(
            () -> {
              var statusDTO = getJobStatus(jobId);
              observedStatuses.add(statusDTO.getStatus());

              return observedStatuses.containsAll(Set.of(RUNNING.name(), COMPLETED.name()));
            });

    var statusDTO = getJobStatus(jobId);
    List<Integer> resultNumbers =
        objectMapper.readValue(statusDTO.getResult(), new TypeReference<>() {});
    assertThat(resultNumbers).isNotNull().hasSize(count);
  }

  @Test
  void testDuplicateJobCreation() {
    var request = new JobRequestDTO(1, 100, 10);

    var jobId = postCreateJob(request);
    assertThat(jobId).isNotBlank();

    postCreateJob(request, HttpStatus.CONFLICT.value());
  }

  @Test
  void testGetNonExistingJob() {
    getJobStatus("non-existing-job", HttpStatus.NOT_FOUND.value());
  }

  @Test
  void testRetriesAndResumeFromCheckpoint() throws Exception {
    var min = 1;
    var max = 100;
    var count = 20;
    var request = new JobRequestDTO(min, max, count);

    var testJobSpy = Mockito.spy(new TestJob());
    ReflectionTestUtils.setField(jobWorker, "testJob", testJobSpy);

    Mockito.doAnswer(
            invocation -> {
              int progress = invocation.getArgument(3);

              int failAtProgress = progress + count / 2;
              var counter = new AtomicInteger(progress);

              return IntStream.range(progress, count)
                  .peek(
                      i -> {
                        if (counter.incrementAndGet() >= failAtProgress) {
                          Mockito.doCallRealMethod()
                              .when(testJobSpy)
                              .run(
                                  Mockito.anyInt(),
                                  Mockito.anyInt(),
                                  Mockito.anyInt(),
                                  Mockito.anyInt());
                          throw new RuntimeException(
                              "Forced failure at progress " + failAtProgress);
                        }
                      })
                  .boxed();
            })
        .when(testJobSpy)
        .run(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());

    var jobId = postCreateJob(request);
    assertThat(jobId).isNotBlank();

    await()
        .atMost(2, SECONDS)
        .pollInterval(100, MILLISECONDS)
        .until(
            () -> {
              var statusDTO = getJobStatus(jobId);
              return statusDTO.getStatus().equals(RUNNING.name()) && statusDTO.getRetries() == 0;
            });

    await()
        .atMost(5, SECONDS)
        .pollInterval(100, MILLISECONDS)
        .until(
            () -> {
              var statusDTO = getJobStatus(jobId);
              return statusDTO.getStatus().equals(COMPLETED.name()) && statusDTO.getRetries() == 1;
            });

    var inOrder = Mockito.inOrder(testJobSpy);
    inOrder.verify(testJobSpy).run(eq(min), eq(max), eq(count), eq(0));
    inOrder.verify(testJobSpy).run(eq(min), eq(max), eq(count), intThat(i -> i > 0));
  }

  @Test
  void testCreateNewJobAAfterCompleted() {
    var request = new JobRequestDTO(1, 100, 5);

    var jobId = postCreateJob(request);
    assertThat(jobId).isNotBlank();

    await()
        .atMost(3, SECONDS)
        .pollInterval(10, MILLISECONDS)
        .until(() -> getJobStatus(jobId).getStatus().equals(COMPLETED.name()));

    var newJobId = postCreateJob(request);
    assertThat(newJobId).isNotBlank();
  }

  @Test
  void testRecoverLostJobs() {
    var min = 1;
    var max = 100;
    var count = 20;
    var progress = 10;
    var jobId = "test-lost-job";
    var jobKey = "job:" + jobId;

    var mockTestJob = mock(TestJob.class);
    ReflectionTestUtils.setField(jobWorker, "testJob", mockTestJob);

    var jobData =
        Map.of(
            "status", RUNNING.name(),
            "min", Integer.toString(min),
            "max", Integer.toString(max),
            "count", Integer.toString(count),
            "progress", Integer.toString(progress),
            "result", "[1,2,3,4,5,6,7,8,9,10]",
            "retries", "0");
    redisTemplate.opsForHash().putAll(jobKey, jobData);

    jobService.recoverLostJobs();

    await()
        .atMost(3, SECONDS)
        .pollInterval(100, MILLISECONDS)
        .until(() -> getJobStatus(jobId).getStatus().equals(COMPLETED.name()));

    verify(mockTestJob, times(1)).run(eq(min), eq(max), eq(count), eq(progress));
  }

  @Test
  void testConcurrentJobExecution() {
    var jobCount = 10;

    var jobIds =
        IntStream.range(0, jobCount)
            .mapToObj(i -> postCreateJob(new JobRequestDTO(1 + i, 100 + i, 5 + i)))
            .toList();

    await()
        .atMost(10, SECONDS)
        .pollInterval(200, MILLISECONDS)
        .until(
            () ->
                jobIds.stream()
                    .allMatch(
                        id -> {
                          var jobStatus = getJobStatus(id);
                          return COMPLETED.name().equals(jobStatus.getStatus())
                              && !jobStatus.getResult().isEmpty();
                        }));
  }

  @Test
  void testMinShouldBeLessThanMax() {
    var request = new JobRequestDTO(100, 1, 5);
    postCreateJob(request, HttpStatus.BAD_REQUEST.value());
  }

  private JobStatusDTO getJobStatus(String jobId, int statusCode) {
    var jsonResponse =
        given().when().get("/jobs/{id}", jobId).then().statusCode(statusCode).extract().asString();
    try {
      return objectMapper.readValue(jsonResponse, JobStatusDTO.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private JobStatusDTO getJobStatus(String jobId) {
    return getJobStatus(jobId, HttpStatus.OK.value());
  }

  private String postCreateJob(JobRequestDTO request) {
    return postCreateJob(request, HttpStatus.OK.value());
  }

  private String postCreateJob(JobRequestDTO request, int statusCode) {
    return given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/jobs")
        .then()
        .statusCode(statusCode)
        .extract()
        .asString();
  }
}
