package br.com.personalfighters.controller.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Data
public class ErrorDetails {

  private int cod;
  private HttpStatus status;
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime timestamp;
  private String message;
  private String path;

  public ErrorDetails(HttpStatus status, LocalDateTime timestamp, String message, String path) {
    this.cod = status.value();
    this.status = status;
    this.timestamp = timestamp;
    this.message = message;
    this.path = path;
  }
}
