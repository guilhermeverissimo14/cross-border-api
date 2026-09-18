package com.guilherme.crossborderapi.repository;

import com.guilherme.crossborderapi.entity.Remittance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemittanceRepository extends JpaRepository<Remittance, Long> {
}
