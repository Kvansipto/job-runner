package com.example.job_runner.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobRequestDTO {

  @NotNull(message = "Min value is required")
  @Min(value = 1, message = "Min must be at least 1")
  @Schema(description = "Minimum value (must be at least 1)", example = "1")
  private Integer min;

  @NotNull(message = "Max value is required")
  @Max(value = 1_000_000, message = "Max must be less than or equal to 1,000,000")
  @Schema(description = "Maximum value (must be ≤ 1,000,000)", example = "1000")
  private Integer max;

  @NotNull(message = "Count value is required")
  @Min(value = 1, message = "Count must be at least 1")
  @Max(value = 10_000, message = "Count must be less than or equal to 10,000")
  @Schema(description = "Number of elements to generate (must be ≤ 10,000)", example = "200")
  private Integer count;

  @AssertTrue(message = "Min must be less than Max")
  @JsonIgnore
  public boolean isMinLessThanMax() {
    return min < max;
  }
}
