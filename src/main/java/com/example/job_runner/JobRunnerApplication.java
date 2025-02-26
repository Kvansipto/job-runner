package com.example.job_runner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JobRunnerApplication {

  public static void main(String[] args) {
    SpringApplication.run(JobRunnerApplication.class, args);
  }
}
