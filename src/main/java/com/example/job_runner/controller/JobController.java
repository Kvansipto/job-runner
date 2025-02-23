package com.example.job_runner.controller;

import com.example.job_runner.dto.JobRequestDTO;
import com.example.job_runner.dto.JobStatusDTO;
import com.example.job_runner.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<String> createJob(@RequestBody JobRequestDTO jobRequestDTO) {
        return ResponseEntity.ok(jobService.startJob(
                jobRequestDTO.getMin(),
                jobRequestDTO.getMax(),
                jobRequestDTO.getCount()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobStatusDTO> getJob(@PathVariable String id) {
        return ResponseEntity.ok(jobService.getJobStatus(id));
    }
}
