package com.example.job_runner.controller;

import com.example.job_runner.dto.JobRequestDTO;
import com.example.job_runner.dto.JobStatusDTO;
import com.example.job_runner.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jobs")
@Validated
@Tag(name = "Job Management", description = "API for job execution and monitoring")
public class JobController {

  private final JobService jobService;

  public JobController(JobService jobService) {
    this.jobService = jobService;
  }

  @PostMapping
  @Operation(
      summary = "Start a new job",
      description = "Creates a job with the given parameters and returns its ID.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Job successfully created",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value = "{ \"jobId\": \"6faea0ae-6d1e-4da5-bb64-a7c0175edb88\" }"))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters",
            content = @Content(mediaType = "application/json")),
        @ApiResponse(
            responseCode = "409",
            description = "Job with identical parameters is already running",
            content = @Content(mediaType = "application/json"))
      })
  public ResponseEntity<Map<String, String>> createJob(
      @Valid @RequestBody JobRequestDTO jobRequestDTO) {
    var jobId =
        jobService.startJob(
            Integer.toString(jobRequestDTO.getMin()),
            Integer.toString(jobRequestDTO.getMax()),
            Integer.toString(jobRequestDTO.getCount()));
    return ResponseEntity.ok(Map.of("jobId", jobId));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get job status",
      description = "Retrieves the status and progress of a job by its ID.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Job status retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = JobStatusDTO.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
                                            {
                                                "status": "COMPLETED",
                                                "progress": "100",
                                                "result": "[12, 45, 78, ...]",
                                                "retries": 0
                                            }
                                            """))),
        @ApiResponse(
            responseCode = "404",
            description = "Job not found",
            content = @Content(mediaType = "application/json"))
      })
  public ResponseEntity<JobStatusDTO> getJob(@PathVariable String id) {
    return ResponseEntity.ok(jobService.getJobStatus(id));
  }
}
