package com.guilherme.crossborderapi.service;

import com.guilherme.crossborderapi.dto.ledger.LedgerEntryResponse;
import com.guilherme.crossborderapi.entity.LedgerEntry;
import com.guilherme.crossborderapi.enums.Currency;
import com.guilherme.crossborderapi.exception.ResourceNotFoundException;
import com.guilherme.crossborderapi.repository.LedgerEntryRepository;
import com.guilherme.crossborderapi.repository.RemittanceRepository;
import com.guilherme.crossborderapi.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final UserRepository userRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final RemittanceRepository remittanceRepository;

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getUserLedgerEntries(Long userId, Currency currency) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuário não encontrado: id=" + userId);
        }

        List<LedgerEntry> entries = currency == null
                ? ledgerEntryRepository.findByWalletUserIdOrderByCreatedAtDescIdDesc(userId)
                : ledgerEntryRepository.findByWalletUserIdAndCurrencyOrderByCreatedAtDescIdDesc(userId, currency);

        return entries.stream().map(LedgerEntryResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getRemittanceLedgerEntries(Long remittanceId, Long requesterId) {
        remittanceRepository.findById(remittanceId)
                .filter(remittance -> remittance.involves(requesterId))
                .orElseThrow(() -> new ResourceNotFoundException("Remessa não encontrada: id=" + remittanceId));

        return ledgerEntryRepository
                .findByReferenceTypeAndReferenceIdOrderByIdAsc(LedgerEntry.REFERENCE_TYPE_REMITTANCE, remittanceId)
                .stream()
                .map(LedgerEntryResponse::fromEntity)
                .toList();
    }
}
