package com.guilherme.crossborderapi.repository;

import com.guilherme.crossborderapi.entity.Remittance;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RemittanceRepository extends JpaRepository<Remittance, Long> {

    Optional<Remittance> findBySenderIdAndIdempotencyKey(Long senderId, String idempotencyKey);

    List<Remittance> findBySenderIdOrderByCreatedAtDescIdDesc(Long senderId);

    Optional<Remittance> findByIdAndSenderId(Long id, Long senderId);

    @Query("SELECT COALESCE(SUM(r.amountBrl), 0) FROM Remittance r "
            + "WHERE r.sender.id = :senderId AND r.createdAt >= :startOfDay AND r.createdAt < :endOfDay")
    BigDecimal sumAmountBrlBySender(@Param("senderId") Long senderId,
                                     @Param("startOfDay") LocalDateTime startOfDay,
                                     @Param("endOfDay") LocalDateTime endOfDay);
}
