package com.example.job_runner.dto;

import lombok.Data;

@Data
public class JobRequestDTO {
    private int min;
    private int max;
    private int count;
}
