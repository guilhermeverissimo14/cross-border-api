package com.guilherme.crossborderapi.service;

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
import com.guilherme.crossborderapi.exception.DailyLimitExceededException;
import com.guilherme.crossborderapi.exception.InsufficientBalanceException;
import com.guilherme.crossborderapi.exception.ResourceNotFoundException;
import com.guilherme.crossborderapi.exception.SelfRemittanceException;
import com.guilherme.crossborderapi.repository.LedgerEntryRepository;
import com.guilherme.crossborderapi.repository.RemittanceRepository;
import com.guilherme.crossborderapi.repository.UserRepository;
import com.guilherme.crossborderapi.repository.WalletRepository;
import com.guilherme.crossborderapi.service.dailylimit.DailyLimitPolicyProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RemittanceService {

    private static final Currency SOURCE_CURRENCY = Currency.BRL;
    private static final Currency TARGET_CURRENCY = Currency.USD;
    private static final int MONEY_SCALE = 2;
    private static final String REMITTANCE_REFERENCE_TYPE = "REMITTANCE";

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final RemittanceRepository remittanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ExchangeRateService exchangeRateService;
    private final DailyLimitPolicyProvider dailyLimitPolicyProvider;

    @Transactional
    public RemittanceResponse createRemittance(RemittanceCreateRequest request) {
        if (request.senderId().equals(request.receiverId())) {
            throw new SelfRemittanceException("Remessa não pode ser enviada para o próprio usuário");
        }

        User sender = findUserOrThrow(request.senderId());
        User receiver = findUserOrThrow(request.receiverId());

        Wallet senderWallet = findWalletOrThrow(sender.getId(), SOURCE_CURRENCY);
        Wallet receiverWallet = findWalletOrThrow(receiver.getId(), TARGET_CURRENCY);

        // Ajusta o valor da remessa para ter duas casas decimais, arredondando para cima se necessário.
        BigDecimal amountBrl = request.amountBrl().setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (senderWallet.getBalance().compareTo(amountBrl) < 0) {
            throw new InsufficientBalanceException("Saldo insuficiente para realizar a remessa");
        }

        validateDailyLimit(sender, amountBrl);

        BigDecimal exchangeRate = exchangeRateService.getBuyRate(LocalDate.now());
        BigDecimal amountUsd = amountBrl.divide(exchangeRate, MONEY_SCALE, RoundingMode.HALF_UP); //mesma coisa de amountBrl / exchangeRate

        senderWallet.setBalance(senderWallet.getBalance().subtract(amountBrl));
        receiverWallet.setBalance(receiverWallet.getBalance().add(amountUsd));
        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        Remittance remittance = Remittance.builder()
                .sender(sender)
                .receiver(receiver)
                .amountBrl(amountBrl)
                .amountUsd(amountUsd)
                .exchangeRate(exchangeRate)
                .direction(RemittanceDirection.BRL_TO_USD)
                .status(RemittanceStatus.COMPLETED)
                .build();
        Remittance savedRemittance = remittanceRepository.save(remittance);

        registerLedgerEntries(senderWallet, receiverWallet, amountBrl, amountUsd, savedRemittance.getId());

        return RemittanceResponse.fromEntity(savedRemittance);
    }

    private void validateDailyLimit(User sender, BigDecimal amountBrl) {
        BigDecimal dailyLimit = dailyLimitPolicyProvider.getDailyLimit(sender.getUserType());

        LocalDate today = LocalDate.now();
        BigDecimal alreadySentToday = remittanceRepository.sumAmountBrlBySender(
                sender.getId(), today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        if (alreadySentToday.add(amountBrl).compareTo(dailyLimit) > 0) {
            throw new DailyLimitExceededException(
                    "Limite diário de R$ " + dailyLimit + " excedido. Já enviado hoje: R$ " + alreadySentToday);
        }
    }

    private void registerLedgerEntries(Wallet senderWallet, Wallet receiverWallet,
                                        BigDecimal amountBrl, BigDecimal amountUsd, Long remittanceId) {
        ledgerEntryRepository.save(LedgerEntry.builder()
                .wallet(senderWallet)
                .currency(SOURCE_CURRENCY)
                .type(LedgerEntryType.DEBIT)
                .amount(amountBrl)
                .referenceType(REMITTANCE_REFERENCE_TYPE)
                .referenceId(remittanceId)
                .build());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .wallet(receiverWallet)
                .currency(TARGET_CURRENCY)
                .type(LedgerEntryType.CREDIT)
                .amount(amountUsd)
                .referenceType(REMITTANCE_REFERENCE_TYPE)
                .referenceId(remittanceId)
                .build());
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: id=" + id));
    }

    private Wallet findWalletOrThrow(Long userId, Currency currency) {
        return walletRepository.findByUserIdAndCurrency(userId, currency)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Carteira " + currency + " não encontrada para o usuário id=" + userId));
    }
}
