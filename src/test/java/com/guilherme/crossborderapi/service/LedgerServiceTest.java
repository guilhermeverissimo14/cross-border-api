package com.guilherme.crossborderapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guilherme.crossborderapi.dto.ledger.LedgerEntryResponse;
import com.guilherme.crossborderapi.entity.LedgerEntry;
import com.guilherme.crossborderapi.enums.Currency;
import com.guilherme.crossborderapi.enums.LedgerEntryType;
import com.guilherme.crossborderapi.exception.ResourceNotFoundException;
import com.guilherme.crossborderapi.repository.LedgerEntryRepository;
import com.guilherme.crossborderapi.repository.RemittanceRepository;
import com.guilherme.crossborderapi.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private RemittanceRepository remittanceRepository;

    @InjectMocks
    private LedgerService ledgerService;

    @Test
    void listsEntriesOfBothWalletsWhenNoCurrencyFilter() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(ledgerEntryRepository.findByWalletUserIdOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of(entry(2L, Currency.USD, LedgerEntryType.CREDIT, "19.39"),
                        entry(1L, Currency.BRL, LedgerEntryType.DEBIT, "100.0000")));

        List<LedgerEntryResponse> result = ledgerService.getUserLedgerEntries(1L, null);

        assertThat(result).extracting(LedgerEntryResponse::id).containsExactly(2L, 1L);
        assertThat(result.get(1).type()).isEqualTo(LedgerEntryType.DEBIT);
        assertThat(result.get(1).amount()).isEqualByComparingTo("100");
        assertThat(result.get(1).referenceType()).isEqualTo("REMITTANCE");
        assertThat(result.get(1).referenceId()).isEqualTo(7L);
        verify(ledgerEntryRepository, never())
                .findByWalletUserIdAndCurrencyOrderByCreatedAtDescIdDesc(1L, Currency.BRL);
    }

    @Test
    void filtersByCurrencyWhenProvided() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(ledgerEntryRepository.findByWalletUserIdAndCurrencyOrderByCreatedAtDescIdDesc(1L, Currency.BRL))
                .thenReturn(List.of(entry(1L, Currency.BRL, LedgerEntryType.DEBIT, "100.0000")));

        List<LedgerEntryResponse> result = ledgerService.getUserLedgerEntries(1L, Currency.BRL);

        assertThat(result).extracting(LedgerEntryResponse::currency).containsExactly(Currency.BRL);
        verify(ledgerEntryRepository, never()).findByWalletUserIdOrderByCreatedAtDescIdDesc(1L);
    }

    @Test
    void throwsWhenUserDoesNotExist() {
        when(userRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> ledgerService.getUserLedgerEntries(999L, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ledgerEntryRepository);
    }

    @Test
    void listsTheTwoLegsOfARemittance() {
        when(remittanceRepository.existsById(7L)).thenReturn(true);
        when(ledgerEntryRepository.findByReferenceTypeAndReferenceIdOrderByIdAsc(LedgerEntry.REFERENCE_TYPE_REMITTANCE, 7L))
                .thenReturn(List.of(entry(1L, Currency.BRL, LedgerEntryType.DEBIT, "100.0000"),
                        entry(2L, Currency.USD, LedgerEntryType.CREDIT, "19.39")));

        List<LedgerEntryResponse> result = ledgerService.getRemittanceLedgerEntries(7L);

        assertThat(result).extracting(LedgerEntryResponse::type)
                .containsExactly(LedgerEntryType.DEBIT, LedgerEntryType.CREDIT);
        assertThat(result).extracting(LedgerEntryResponse::currency)
                .containsExactly(Currency.BRL, Currency.USD);
    }

    @Test
    void throwsWhenRemittanceDoesNotExist() {
        when(remittanceRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> ledgerService.getRemittanceLedgerEntries(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ledgerEntryRepository);
    }

    private LedgerEntry entry(Long id, Currency currency, LedgerEntryType type, String amount) {
        return LedgerEntry.builder()
                .id(id)
                .currency(currency)
                .type(type)
                .amount(new BigDecimal(amount))
                .referenceType("REMITTANCE")
                .referenceId(7L)
                .build();
    }
}
