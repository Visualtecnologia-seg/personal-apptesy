package br.com.personalfighters.service;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.ProfessionalAgenda;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Status;

import java.time.DayOfWeek;
import java.util.List;

public interface ProfessionalAgendaService {

  Iterable<ProfessionalAgenda> findByUserIdAndDate(Long id, String date);

  void createProfessionalAgenda(User user);

  ProfessionalAgenda save(ProfessionalAgenda professionalAgenda);

  Iterable<ProfessionalAgenda> findByUserId(Long id);

  List<User> findProfessionalsByDayAndHour(Order order, List<User> users);

  Long countProfessionalsByDayAndHour(String time, DayOfWeek dayOfWeek, Status open, List<User> list);

}
