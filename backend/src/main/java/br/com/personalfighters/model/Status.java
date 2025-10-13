package br.com.personalfighters.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Status {
  OPEN("OPEN"), CONFIRMED("CONFIRMED"), DONE("DONE"), BLOCKED("BLOCKED"),
  CANCELLED("CANCELLED");
  private final String status;
}
