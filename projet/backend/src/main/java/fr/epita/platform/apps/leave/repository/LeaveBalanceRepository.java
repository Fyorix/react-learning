package fr.epita.platform.apps.leave.repository;

import fr.epita.platform.apps.leave.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, LeaveBalance.Key> {

    List<LeaveBalance> findByIdYear(int year);
}
