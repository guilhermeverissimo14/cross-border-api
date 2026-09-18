package com.guilherme.crossborderapi.repository;

import com.guilherme.crossborderapi.entity.LedgerEntry;
import com.guilherme.crossborderapi.enums.Currency;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByWalletUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<LedgerEntry> findByWalletUserIdAndCurrencyOrderByCreatedAtDescIdDesc(Long userId, Currency currency);
}
