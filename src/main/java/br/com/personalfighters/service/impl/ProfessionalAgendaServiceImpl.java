package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.ProfessionalAgenda;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Gender;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.ProfessionalAgendaRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.ProfessionalAgendaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfessionalAgendaServiceImpl implements ProfessionalAgendaService {

  private final ProfessionalAgendaRepository professionalAgendaRepository;
  private final UserRepository userRepository;
  private final OrderRepository orderRepository;

  @Override
  public Iterable<ProfessionalAgenda> findByUserIdAndDate(Long id, String date) {
    User professional = userRepository.findById(id).orElseThrow();
    Iterable<ProfessionalAgenda> agendas = professionalAgendaRepository.findByUserId(professional);

    // Pegar todas as datas e horários ocupados de segunda a domingo
    LocalDate localDate = LocalDate.parse(date);
    int remainder = (localDate.getDayOfWeek().getValue() - 7) * -1;
    LocalDate monday = localDate.minusDays(localDate.getDayOfWeek().getValue() - 1);
    LocalDate sunday = localDate.plusDays(remainder);
    Iterable<Order> allConfirmedOrdersByProfessional = orderRepository.findByProfessionalAndConfirmed(professional, Status.CONFIRMED, monday, sunday);
    Iterable<Order> allRespondedOrdersByProfessional = orderRepository.findByProfessionalAndResponded(professional, monday, sunday);

    // Criando uma agenda nova
    List<ProfessionalAgenda> newAgenda = new ArrayList<>();
    agendas.forEach(newAgenda::add);

    List<List<String>> timeList = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      timeList.add(new ArrayList<>());
    }

    allConfirmedOrdersByProfessional.forEach(item -> {
      int index = item.getDate().getDayOfWeek().getValue() - 1;
      timeList.get(index).add(item.getStartTime().toString());
    });

    allRespondedOrdersByProfessional.forEach(item -> {
      int index = item.getDate().getDayOfWeek().getValue() - 1;
      timeList.get(index).add(item.getStartTime().toString());
    });

    JSONArray result = new JSONArray();
    agendas.forEach(professionalAgenda -> {
      JSONArray array = new JSONArray(professionalAgenda.getSchedule());
      int index = professionalAgenda.getDayOfWeek().getValue() - 1;

      System.out.println(professionalAgenda.getDayOfWeek() + ", " + professionalAgenda.getDayOfWeek().getValue() + ", index: " + index);

      AtomicInteger count = new AtomicInteger();
      array.forEach(time -> {
        String hour = time.toString().substring(2, 7);
        boolean hasHour = timeList.get(index).toString().contains(hour);
        if (hasHour) {
          LocalTime localTime = LocalTime.parse(hour);
          array.put(count.intValue(), new JSONObject().put(localTime.toString(), false));
          array.put(count.intValue() + 1, new JSONObject().put(localTime.plusMinutes(30).toString(), false));
        }
        count.getAndIncrement();
      });

      result.put(array);
      newAgenda.get(index).setSchedule(array.toString());
    });
    return newAgenda;
  }

  @Override
  public void createProfessionalAgenda(User user) {
    for (int i = 0; i < 7; i++) {
      ProfessionalAgenda professionalAgenda = new ProfessionalAgenda();
      professionalAgenda.setUser(user);
      professionalAgenda.setDayOfWeek(DayOfWeek.MONDAY.plus(i));
      professionalAgenda.setStatus(Status.OPEN);

      JSONArray jsonArray = new JSONArray();
      LocalTime start = LocalTime.parse("06:00");
      for (int j = 0; j < 36; j++) {
        JSONObject schedule = new JSONObject();
        String hour = String.valueOf(start.plusMinutes(j * 30).getHour());
        if (start.plusMinutes(j * 30).getHour() <= 9) {
          hour = "0" + hour;
        }
        String minute = String.valueOf(start.plusMinutes(j * 30).getMinute());
        if (start.plusMinutes(j * 30).getMinute() == 0) {
          minute = "0" + minute;
        }
        String time = hour + ":" + minute;

        schedule.put(time, true);
        jsonArray.put(schedule);
      }
      professionalAgenda.setSchedule(jsonArray.toString());
      professionalAgendaRepository.save(professionalAgenda);
    }
  }

  @Override
  public ProfessionalAgenda save(ProfessionalAgenda professionalAgenda) {
    return professionalAgendaRepository.save(professionalAgenda);
  }

  @Override
  public Iterable<ProfessionalAgenda> findByUserId(Long id) {
    User user = userRepository.findById(id).orElseThrow();
    return professionalAgendaRepository.findByUserId(user);
  }

  @Override
  public List<User> findProfessionalsByDayAndHour(Order order, List<User> list) {
    String schedule = "{\"" + order.getStartTime() + "\":true}" + ",{\"" + order.getStartTime().plusMinutes(30) + "\":true}";
    DayOfWeek dayOfWeek = order.getDate().getDayOfWeek();
    if (order.getGender() == null || order.getGender() == Gender.ANY) {
      return professionalAgendaRepository.findProfessionalsByDayAndHour(dayOfWeek, Status.OPEN, schedule, list);
    } else {
      return professionalAgendaRepository.findProfessionalsByDayAndHourAndGender(dayOfWeek, Status.OPEN, schedule, order.getGender(), list);
    }
  }

  @Override
  public Long countProfessionalsByDayAndHour(String schedule, DayOfWeek dayOfWeek, Status status, List<User> list) {
    return professionalAgendaRepository.countProfessionalsByDayAndHour(dayOfWeek, status, schedule, list);
  }

}
