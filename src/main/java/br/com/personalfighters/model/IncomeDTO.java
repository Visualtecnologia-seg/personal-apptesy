package br.com.personalfighters.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class IncomeDTO {

  private Long id;

  private LocalDate firstDateOfWeek;
}
