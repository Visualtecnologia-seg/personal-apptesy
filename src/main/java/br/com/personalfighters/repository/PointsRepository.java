package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Points;
import br.com.personalfighters.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PointsRepository extends JpaRepository<Points, Long> {
  List<Points> findByUserOrderByCreatedAtDesc(User user);

  @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Points p WHERE p.user = :user")
  Integer sumAmountByUser(@Param("user") User user);
}
