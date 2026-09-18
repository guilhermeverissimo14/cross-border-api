package com.guilherme.crossborderapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guilherme.crossborderapi.dto.remittance.RemittanceCreateRequest;
import com.guilherme.crossborderapi.dto.remittance.RemittanceResponse;
import com.guilherme.crossborderapi.entity.LedgerEntry;
import com.guilherme.crossborderapi.entity.Remittance;
import com.guilherme.crossborderapi.entity.User;
import com.guilherme.crossborderapi.entity.Wallet;
import com.guilherme.crossborderapi.enums.Currency;
import com.guilherme.crossborderapi.enums.LedgerEntryType;
import com.guilherme.crossborderapi.enums.RemittanceDirection;
import com.guilherme.crossborderapi.enums.RemittanceStatus;
import com.guilherme.crossborderapi.enums.UserType;
import com.guilherme.crossborderapi.exception.DailyLimitExceededException;
import com.guilherme.crossborderapi.exception.IdempotencyKeyInUseException;
import com.guilherme.crossborderapi.exception.IdempotencyKeyReuseException;
import com.guilherme.crossborderapi.exception.InsufficientBalanceException;
import com.guilherme.crossborderapi.exception.InvalidIdempotencyKeyException;
import com.guilherme.crossborderapi.exception.ResourceNotFoundException;
import com.guilherme.crossborderapi.exception.SelfRemittanceException;
import com.guilherme.crossborderapi.repository.LedgerEntryRepository;
import com.guilherme.crossborderapi.repository.RemittanceRepository;
import com.guilherme.crossborderapi.repository.UserRepository;
import com.guilherme.crossborderapi.repository.WalletRepository;
import com.guilherme.crossborderapi.service.dailylimit.DailyLimitPolicyProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class RemittanceServiceTest {

    private static final String KEY = "remessa-0001";

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

    @Mock
    private DailyLimitPolicyProvider dailyLimitPolicyProvider;

    @InjectMocks
    private RemittanceService remittanceService;

    @Test
    void throwsWhenSenderAndReceiverAreTheSame() {
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 1L, BigDecimal.TEN);

        assertThatThrownBy(() -> remittanceService.createRemittance(request, KEY))
                .isInstanceOf(SelfRemittanceException.class);
    }

    @Test
    void throwsWhenSenderDoesNotExist() {
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 2L, BigDecimal.TEN);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> remittanceService.createRemittance(request, KEY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsWhenReceiverDoesNotExist() {
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 2L, BigDecimal.TEN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> remittanceService.createRemittance(request, KEY))
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

        assertThatThrownBy(() -> remittanceService.createRemittance(request, KEY))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void throwsWhenDailyLimitExceeded() {
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 2L, new BigDecimal("1000.00"));
        User sender = User.builder().id(1L).userType(UserType.PF).build();
        User receiver = User.builder().id(2L).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(walletRepository.findByUserIdAndCurrency(1L, Currency.BRL))
                .thenReturn(Optional.of(Wallet.builder().balance(new BigDecimal("5000.00")).build()));
        when(walletRepository.findByUserIdAndCurrency(2L, Currency.USD))
                .thenReturn(Optional.of(Wallet.builder().balance(BigDecimal.ZERO).build()));
        when(dailyLimitPolicyProvider.getDailyLimit(UserType.PF)).thenReturn(new BigDecimal("10000.00"));
        when(remittanceRepository.sumAmountBrlBySender(any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("9500.00")); // já enviou 9500 hoje, +1000 estouraria os 10000

        assertThatThrownBy(() -> remittanceService.createRemittance(request, KEY))
                .isInstanceOf(DailyLimitExceededException.class);
    }

    @Test
    void createsRemittanceSuccessfully() {
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 2L, new BigDecimal("1000.00"));
        Wallet senderWallet = Wallet.builder().balance(new BigDecimal("2000.00")).build();
        Wallet receiverWallet = Wallet.builder().balance(BigDecimal.ZERO).build();
        stubValidRemittanceScenario(senderWallet, receiverWallet);
        when(remittanceRepository.save(any(Remittance.class))).thenAnswer(invocation -> {
            Remittance remittance = invocation.getArgument(0);
            remittance.setId(100L);
            return remittance;
        });

        RemittanceResponse response = remittanceService.createRemittance(request, KEY);

        assertThat(response.amountBrl()).isEqualByComparingTo("1000.00");
        assertThat(response.amountUsd()).isEqualByComparingTo("200.00");
        assertThat(response.exchangeRate()).isEqualByComparingTo("5.00");
        assertThat(senderWallet.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(receiverWallet.getBalance()).isEqualByComparingTo("200.00");

        ArgumentCaptor<Remittance> remittanceCaptor = ArgumentCaptor.forClass(Remittance.class);
        verify(remittanceRepository).save(remittanceCaptor.capture());
        assertThat(remittanceCaptor.getValue().getIdempotencyKey()).isEqualTo(KEY);

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

    // Idempotência

    @Test
    void replaysPreviousRemittanceWithoutReprocessing() {
        when(remittanceRepository.findBySenderIdAndIdempotencyKey(1L, KEY))
                .thenReturn(Optional.of(previousRemittance()));

        RemittanceResponse response = remittanceService.createRemittance(
                new RemittanceCreateRequest(1L, 2L, new BigDecimal("1000.00")), KEY);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.amountBrl()).isEqualByComparingTo("1000.00");
        assertThat(response.amountUsd()).isEqualByComparingTo("200.00");
        // Nada além da consulta pela chave: não valida saldo/limite, não busca cotação, não mexe em
        // carteira, não grava remessa nem ledger.
        verifyNoInteractions(userRepository, walletRepository, ledgerEntryRepository,
                exchangeRateService, dailyLimitPolicyProvider);
        verify(remittanceRepository, never()).save(any());
    }

    @Test
    void replayIgnoresDifferenceInDecimalScaleOfTheAmount() {
        when(remittanceRepository.findBySenderIdAndIdempotencyKey(1L, KEY))
                .thenReturn(Optional.of(previousRemittance()));

        RemittanceResponse response = remittanceService.createRemittance(
                new RemittanceCreateRequest(1L, 2L, new BigDecimal("1000")), KEY); // "1000" == "1000.00"

        assertThat(response.id()).isEqualTo(100L);
    }

    @Test
    void throwsWhenKeyIsReusedWithDifferentAmount() {
        when(remittanceRepository.findBySenderIdAndIdempotencyKey(1L, KEY))
                .thenReturn(Optional.of(previousRemittance()));

        assertThatThrownBy(() -> remittanceService.createRemittance(
                new RemittanceCreateRequest(1L, 2L, new BigDecimal("999.00")), KEY))
                .isInstanceOf(IdempotencyKeyReuseException.class);
        verify(remittanceRepository, never()).save(any());
    }

    @Test
    void throwsWhenKeyIsReusedWithDifferentReceiver() {
        when(remittanceRepository.findBySenderIdAndIdempotencyKey(1L, KEY))
                .thenReturn(Optional.of(previousRemittance()));

        assertThatThrownBy(() -> remittanceService.createRemittance(
                new RemittanceCreateRequest(1L, 3L, new BigDecimal("1000.00")), KEY))
                .isInstanceOf(IdempotencyKeyReuseException.class);
        verify(remittanceRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "chave com espaço", "abc\r\nFAKE LOG LINE", "a/b", "ação"})
    void throwsWhenIdempotencyKeyHasInvalidFormat(String invalidKey) {
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 2L, BigDecimal.TEN);

        assertThatThrownBy(() -> remittanceService.createRemittance(request, invalidKey))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        verifyNoInteractions(remittanceRepository);
    }

    @Test
    void throwsWhenIdempotencyKeyIsLongerThanTheColumn() {
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 2L, BigDecimal.TEN);

        assertThatThrownBy(() -> remittanceService.createRemittance(request, "a".repeat(65)))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    void acceptsIdempotencyKeyWithExactly64Characters() {
        // Passa da validação da chave e só então cai na regra de remessa para si mesmo.
        RemittanceCreateRequest request = new RemittanceCreateRequest(1L, 1L, BigDecimal.TEN);

        assertThatThrownBy(() -> remittanceService.createRemittance(request, "a".repeat(64)))
                .isInstanceOf(SelfRemittanceException.class);
    }

    @Test
    void translatesConcurrentDuplicateIntoIdempotencyKeyInUse() {
        Wallet senderWallet = Wallet.builder().balance(new BigDecimal("2000.00")).build();
        Wallet receiverWallet = Wallet.builder().balance(BigDecimal.ZERO).build();
        stubValidRemittanceScenario(senderWallet, receiverWallet);
        // Outra requisição com a mesma chave gravou primeiro: a constraint UNIQUE recusa a nossa.
        when(remittanceRepository.save(any(Remittance.class)))
                .thenThrow(new DataIntegrityViolationException("unique key violation"));

        assertThatThrownBy(() -> remittanceService.createRemittance(
                new RemittanceCreateRequest(1L, 2L, new BigDecimal("1000.00")), KEY))
                .isInstanceOf(IdempotencyKeyInUseException.class);
        verify(ledgerEntryRepository, never()).save(any());
    }

    private void stubValidRemittanceScenario(Wallet senderWallet, Wallet receiverWallet) {
        User sender = User.builder().id(1L).userType(UserType.PF).build();
        User receiver = User.builder().id(2L).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(walletRepository.findByUserIdAndCurrency(1L, Currency.BRL)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserIdAndCurrency(2L, Currency.USD)).thenReturn(Optional.of(receiverWallet));
        when(dailyLimitPolicyProvider.getDailyLimit(UserType.PF)).thenReturn(new BigDecimal("10000.00"));
        when(remittanceRepository.sumAmountBrlBySender(any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(BigDecimal.ZERO);
        when(exchangeRateService.getBuyRate(any(LocalDate.class))).thenReturn(new BigDecimal("5.00"));
    }

    private Remittance previousRemittance() {
        return Remittance.builder()
                .id(100L)
                .sender(User.builder().id(1L).build())
                .receiver(User.builder().id(2L).build())
                .amountBrl(new BigDecimal("1000.00"))
                .amountUsd(new BigDecimal("200.00"))
                .exchangeRate(new BigDecimal("5.00"))
                .direction(RemittanceDirection.BRL_TO_USD)
                .status(RemittanceStatus.COMPLETED)
                .idempotencyKey(KEY)
                .build();
    }
}
