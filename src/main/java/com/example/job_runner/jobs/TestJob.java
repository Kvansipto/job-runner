package com.example.job_runner.jobs;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class TestJob {
  public Stream<Integer> run(int min, int max, int count, int startProgress) {
    var counter = new AtomicInteger(startProgress);
    return Stream.generate(
            () -> {
              counter.incrementAndGet();
              return (int) (Math.random() * max + min);
            })
        .takeWhile(n -> counter.get() <= count);
  }
}
