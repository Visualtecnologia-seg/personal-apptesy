package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Finance;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.FinanceDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FinanceRepository extends JpaRepository<Finance, Long> {

  Finance findByUser(User customer);
  // TODO Filtrar somente profissionais
  @Query("select new br.com.personalfighters.model.FinanceDTO(u.id, u.name, u.surname, u.avatarUrl, u.cpf, u.email, u.active, f.customerPaymentProfile, f.professionalBalance, b.pixCpf, b.pixPhoneNumber, b.pixEmail) " +
      "from Finance f " +
      "inner join User u on u.id = f.id " +
      "inner join BankData b on u.id = b.professional.id")
  Page<FinanceDTO> findAllFinanceData(Pageable pageable);

}
