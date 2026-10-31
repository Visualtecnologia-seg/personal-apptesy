package br.com.personalfighters.repository;

import br.com.personalfighters.entity.ProfessionalAgenda;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Gender;
import br.com.personalfighters.model.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.List;

public interface ProfessionalAgendaRepository extends JpaRepository<ProfessionalAgenda, Long> {

  @Query("select pa from ProfessionalAgenda as pa where pa.user =:user order by pa.id")
  Iterable<ProfessionalAgenda> findByUserId(User user);

  @Query("select count(pa.user) from ProfessionalAgenda pa where pa.user in (:list) and pa.dayOfWeek=:dayOfWeek and pa.status =:status " +
      "and pa.schedule like %:schedule%")
  Long countProfessionalsByDayAndHour(
      @Param("dayOfWeek") DayOfWeek dayOfWeek,
      @Param("status") Status status,
      @Param("schedule") String schedule,
      @Param("list") List<User> list);

  @Query("select pa.user from ProfessionalAgenda pa where pa.user in (:list) and pa.dayOfWeek=:dayOfWeek and pa.status =:status " +
      "and pa.schedule like %:schedule%")
  List<User> findProfessionalsByDayAndHour(
      @Param("dayOfWeek") DayOfWeek dayOfWeek,
      @Param("status") Status status,
      @Param("schedule") String schedule,
      @Param("list") List<User> list);


  @Query("select pa.user from ProfessionalAgenda pa where pa.user in (:list) and pa.dayOfWeek=:dayOfWeek and pa.status =:status " +
      "and pa.user.gender =:gender and pa.schedule like %:schedule%")
  List<User> findProfessionalsByDayAndHourAndGender(
      @Param("dayOfWeek") DayOfWeek dayOfWeek,
      @Param("status") Status status,
      @Param("schedule") String schedule,
      @Param("gender") Gender gender,
      @Param("list") List<User> list);
}
