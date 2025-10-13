package br.com.personalfighters.controller.exception;

import br.com.personalfighters.service.impl.exceptions.DataIntegrityException;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import br.com.personalfighters.service.impl.exceptions.PaymentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  //handle specific exceptions
  @ExceptionHandler(ObjectNotFoundException.class)
  public ResponseEntity<ErrorDetails> handleObjectNotFoundException(ObjectNotFoundException exception, HttpServletRequest request) {
    ErrorDetails err = new ErrorDetails(
        HttpStatus.NOT_FOUND,
        LocalDateTime.now(ZoneId.of("America/Sao_Paulo")),
        exception.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(err.getStatus()).body(err);
  }

  @ExceptionHandler(PaymentException.class)
  public ResponseEntity<ErrorDetails> handlePaymentErrors(PaymentException exception, HttpServletRequest request) {
    ErrorDetails err = new ErrorDetails(
        HttpStatus.INTERNAL_SERVER_ERROR,
        LocalDateTime.now(ZoneId.of("America/Sao_Paulo")),
        exception.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(err.getStatus()).body(err);
  }

  @ExceptionHandler(DataIntegrityException.class)
  public ResponseEntity<ErrorDetails> dataIntegrity(DataIntegrityException exception, HttpServletRequest request) {
    ErrorDetails err = new ErrorDetails(
        HttpStatus.BAD_REQUEST,
        LocalDateTime.now(ZoneId.of("America/Sao_Paulo")),
        exception.getMessage(),
        request.getRequestURI()
    );

    return ResponseEntity.status(err.getStatus()).body(err);
  }

  //handle global exceptions
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorDetails> handleGlobalException(Exception exception, HttpServletRequest request) {
    ErrorDetails err = new ErrorDetails(
        HttpStatus.INTERNAL_SERVER_ERROR, LocalDateTime.now(ZoneId.of("America/Sao_Paulo")),
        exception.getMessage(),
        request.getRequestURI()
    );

    return ResponseEntity.status(err.getStatus()).body(err);
  }
}
