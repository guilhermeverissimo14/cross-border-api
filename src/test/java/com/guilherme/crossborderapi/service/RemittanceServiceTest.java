package com.guilherme.crossborderapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guilherme.crossborderapi.dto.remittance.RemittanceCreateRequest;
import com.guilherme.crossborderapi.dto.remittance.RemittanceResponse;
import com.guilherme.crossborderapi.entity.LedgerEntry;
import com.guilherme.crossborderapi.entity.Remittance;
import com.guilherme.crossborderapi.entity.User;
import com.guilherme.crossborderapi.entity.Wallet;
import com.guilherme.crossborderapi.enums.Currency;
import com.guilherme.crossborderapi.enums.LedgerEntryType;
import com.guilherme.crossborderapi.exception.InsufficientBalanceException;
import com.guilherme.crossborderapi.exception.ResourceNotFoundException;
import com.guilherme.crossborderapi.exception.SelfRemittanceException;
import com.guilherme.crossborderapi.repository.LedgerEntryRepository;
import com.guilherme.crossborderapi.repository.RemittanceRepository;
import com.guilherme.crossborderapi.repository.UserRepository;
import com.guilherme.crossborderapi.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemittanceServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private RemittanceRepository remittanceRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private RemittanceService remittanceService;

    @Test
    void throwsWhenSenderAndReceiverAreTheSame() {
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 1L, BigDecimal.TEN);

        assertThatThrownBy(() -> remittanceService.createRemittance(request))
                .isInstanceOf(SelfRemittanceException.class);
    }

    @Test
    void throwsWhenSenderDoesNotExist() {
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 2L, BigDecimal.TEN);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> remittanceService.createRemittance(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsWhenReceiverDoesNotExist() {
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 2L, BigDecimal.TEN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> remittanceService.createRemittance(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsWhenSenderHasInsufficientBalance() {
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 2L, new BigDecimal("1000.00"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
        when(walletRepository.findByUserIdAndCurrency(1L, Currency.BRL))
                .thenReturn(Optional.of(Wallet.builder().balance(new BigDecimal("500.00")).build()));
        when(walletRepository.findByUserIdAndCurrency(2L, Currency.USD))
                .thenReturn(Optional.of(Wallet.builder().balance(BigDecimal.ZERO).build()));

        assertThatThrownBy(() -> remittanceService.createRemittance(request))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void createsRemittanceSuccessfully() {
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 2L, new BigDecimal("1000.00"));
        User sender = User.builder().id(1L).build();
        User receiver = User.builder().id(2L).build();
        Wallet senderWallet = Wallet.builder().balance(new BigDecimal("2000.00")).build();
        Wallet receiverWallet = Wallet.builder().balance(BigDecimal.ZERO).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(walletRepository.findByUserIdAndCurrency(1L, Currency.BRL)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserIdAndCurrency(2L, Currency.USD)).thenReturn(Optional.of(receiverWallet));
        when(exchangeRateService.getBuyRate(any(LocalDate.class))).thenReturn(new BigDecimal("5.00"));
        when(remittanceRepository.save(any(Remittance.class))).thenAnswer(invocation -> {
            Remittance remittance = invocation.getArgument(0);
            remittance.setId(100L);
            return remittance;
        });

        RemittanceResponse response = remittanceService.createRemittance(request);

        assertThat(response.amountBrl()).isEqualByComparingTo("1000.00");
        assertThat(response.amountUsd()).isEqualByComparingTo("200.00");
        assertThat(response.exchangeRate()).isEqualByComparingTo("5.00");
        assertThat(senderWallet.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(receiverWallet.getBalance()).isEqualByComparingTo("200.00");

        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(ledgerCaptor.capture());

        LedgerEntry debitEntry = ledgerCaptor.getAllValues().get(0);
        assertThat(debitEntry.getType()).isEqualTo(LedgerEntryType.DEBIT);
        assertThat(debitEntry.getCurrency()).isEqualTo(Currency.BRL);
        assertThat(debitEntry.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(debitEntry.getReferenceId()).isEqualTo(100L);

        LedgerEntry creditEntry = ledgerCaptor.getAllValues().get(1);
        assertThat(creditEntry.getType()).isEqualTo(LedgerEntryType.CREDIT);
        assertThat(creditEntry.getCurrency()).isEqualTo(Currency.USD);
        assertThat(creditEntry.getAmount()).isEqualByComparingTo("200.00");
        assertThat(creditEntry.getReferenceId()).isEqualTo(100L);
    }
}
