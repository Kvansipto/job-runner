package com.example.job_runner.controller;

import com.example.job_runner.dto.JobRequestDTO;
import com.example.job_runner.dto.JobStatusDTO;
import com.example.job_runner.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jobs")
@Validated
public class JobController {

  private final JobService jobService;

  public JobController(JobService jobService) {
    this.jobService = jobService;
  }

  @PostMapping
  public ResponseEntity<String> createJob(@Valid @RequestBody JobRequestDTO jobRequestDTO) {
    return ResponseEntity.ok(
        jobService.startJob(
            Integer.toString(jobRequestDTO.getMin()),
            Integer.toString(jobRequestDTO.getMax()),
            Integer.toString(jobRequestDTO.getCount())));
  }

  @GetMapping("/{id}")
  public ResponseEntity<JobStatusDTO> getJob(@PathVariable String id) {
    return ResponseEntity.ok(jobService.getJobStatus(id));
  }
}
