package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Product;
import br.com.personalfighters.entity.ProductAgenda;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.ProductAgendaRepository;
import br.com.personalfighters.repository.ProductRepository;
import br.com.personalfighters.service.ProductAgendaService;
import br.com.personalfighters.service.ProfessionalAgendaService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductAgendaServiceImpl implements ProductAgendaService {

  private final ProductAgendaRepository productAgendaRepository;
  private final ProfessionalAgendaService professionalAgendaService;
  private final ProductRepository productRepository;

  private final List<String> times = getTimeList();

  @Override
  public Iterable<ProductAgenda> findByProductId(Long id) {
    Product product = productRepository.findById(id).orElseThrow(() ->
        new ObjectNotFoundException("Produto não encontrado - ID: " + id));
    update(product);
    return productAgendaRepository.findByProduct(product);
  }

  @Override
  public void save(Product product) {
    try {
      for (int i = 0; i < 7; i++) {
        ProductAgenda agenda = new ProductAgenda();
        agenda.setDayOfWeek(DayOfWeek.MONDAY.plus(i));
        JSONArray schedule = createSchedule(product, agenda.getDayOfWeek());
        agenda.setProduct(product);
        agenda.setSchedule(schedule.toString());
        productAgendaRepository.save(agenda);
      }
    } catch (JSONException e) {
      log.error("Error on save", e);
    }
  }

  @Override
  public void update(Product product) {
    Iterable<ProductAgenda> week = productAgendaRepository.findByProduct(product);
    try {
      week.forEach(day -> {
        JSONArray schedule = createSchedule(product, day.getDayOfWeek());
        day.setSchedule(schedule.toString());
        productAgendaRepository.save(day);
      });
    } catch (JSONException e) {
      log.error("Error on update", e);
    }
  }

  @Override
  public void updateAllProductAgendas() {
    Iterable<Product> products = productRepository.findAll();
    products.forEach(this::update);
  }

  private List<String> getTimeList() {
    List<String> times = new ArrayList<>();
    for (int x = 6; x < 24; x++) {
      String hour = x < 10 ? "0" + x : String.valueOf(x);
      for (int y = 0; y < 2; y++) {
        String minute = y == 0 ? "00" : "30";
        times.add(hour + ":" + minute);
      }
    }
    return times;
  }

  private JSONArray createSchedule(Product product, DayOfWeek dayOfWeek) {
    JSONArray schedule = new JSONArray();
    List<User> list = findAllProfessionalsByProduct(product);

    for (String time : times) {
      JSONObject object = new JSONObject();
      boolean hasProfessionals = professionalAgendaService.countProfessionalsByDayAndHour
          ("\"" + time + "\"" + ":" + true, dayOfWeek, Status.OPEN, list) != 0;
      object.put(time, hasProfessionals);
      schedule.put(object);
    }
    return schedule;
  }

  private List<User> findAllProfessionalsByProduct(Product product) {
    return productRepository.findAllProfessionalsByProduct(product);
  }
}
