package com.example.job_runner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JobStatusDTO {
    private String status;
    private String progress;
    private String result;
}
