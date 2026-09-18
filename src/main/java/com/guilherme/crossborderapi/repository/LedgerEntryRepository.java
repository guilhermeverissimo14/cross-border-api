package com.guilherme.crossborderapi.repository;

import com.guilherme.crossborderapi.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
}
