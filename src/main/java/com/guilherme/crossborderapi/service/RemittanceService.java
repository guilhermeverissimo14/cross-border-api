package com.guilherme.crossborderapi.service;

import com.guilherme.crossborderapi.dto.exchangerate.ExchangeRateQuote;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemittanceService {

    private static final Currency SOURCE_CURRENCY = Currency.BRL;
    private static final Currency TARGET_CURRENCY = Currency.USD;
    private static final int MONEY_SCALE = 2;

    private static final Pattern IDEMPOTENCY_KEY_FORMAT = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final RemittanceRepository remittanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ExchangeRateService exchangeRateService;
    private final DailyLimitPolicyProvider dailyLimitPolicyProvider;

    @Transactional
    public RemittanceResponse createRemittance(Long senderId, RemittanceCreateRequest request, String idempotencyKey) {
        validateIdempotencyKeyFormat(idempotencyKey);

        log.info("Iniciando remessa: senderId={} receiverId={} amountBrl={} idempotencyKey={}",
                senderId, request.receiverId(), request.amountBrl(), idempotencyKey);

       //Verifica se já existe essa key no banco
        Optional<Remittance> previous =
                remittanceRepository.findBySenderIdAndIdempotencyKey(senderId, idempotencyKey);
        if (previous.isPresent()) {
            return replay(previous.get(), request);
        }

        if (senderId.equals(request.receiverId())) {
            throw new SelfRemittanceException("Remessa não pode ser enviada para o próprio usuário");
        }

        User sender = findUserOrThrow(senderId);
        User receiver = findUserOrThrow(request.receiverId());

        Wallet senderWallet = findWalletOrThrow(sender.getId(), SOURCE_CURRENCY);
        Wallet receiverWallet = findWalletOrThrow(receiver.getId(), TARGET_CURRENCY);

        // Ajusta o valor da remessa para ter duas casas decimais, arredondando para cima se necessário.
        BigDecimal amountBrl = request.amountBrl().setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (senderWallet.getBalance().compareTo(amountBrl) < 0) {
            throw new InsufficientBalanceException("Saldo insuficiente para realizar a remessa");
        }

        validateDailyLimit(sender, amountBrl);

        ExchangeRateQuote quote = exchangeRateService.getQuote(LocalDate.now());
        BigDecimal exchangeRate = quote.buyRate();
        BigDecimal amountUsd = amountBrl.divide(exchangeRate, MONEY_SCALE, RoundingMode.HALF_UP); //amountBrl / exchangeRate

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
                .quoteDate(quote.quoteDate())
                .direction(RemittanceDirection.BRL_TO_USD)
                .status(RemittanceStatus.COMPLETED)
                .idempotencyKey(idempotencyKey)
                .build();
        Remittance savedRemittance = saveRemittance(remittance);

        registerLedgerEntries(senderWallet, receiverWallet, amountBrl, amountUsd, savedRemittance.getId());

        // "registrada" e não "concluída": o commit da transação só acontece depois que o método retorna.
        log.info("Remessa registrada: remittanceId={} amountBrl={} amountUsd={} cotacao={}",
                savedRemittance.getId(), amountBrl, amountUsd, exchangeRate);

        return RemittanceResponse.fromEntity(savedRemittance);
    }

    @Transactional(readOnly = true)
    public RemittanceResponse getRemittanceById(Long id, Long requesterId) {
        // Quem não participa da remessa recebe 404, igual a uma remessa que não existe
        return remittanceRepository.findById(id)
                .filter(remittance -> remittance.involves(requesterId))
                .map(RemittanceResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Remessa não encontrada: id=" + id));
    }

  
    @Transactional(readOnly = true)
    public List<RemittanceResponse> getRemittancesSentByUser(Long userId, Long remittanceId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuário não encontrado: id=" + userId);
        }

        List<Remittance> remittances = remittanceId == null
                ? remittanceRepository.findBySenderIdOrderByCreatedAtDescIdDesc(userId)
                : remittanceRepository.findByIdAndSenderId(remittanceId, userId).stream().toList();

        return remittances.stream().map(RemittanceResponse::fromEntity).toList();
    }

    private void validateIdempotencyKeyFormat(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_FORMAT.matcher(idempotencyKey).matches()) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency-Key inválida: use de 1 a 64 caracteres entre letras, números, '.', '_' e '-'");
        }
    }

    private RemittanceResponse replay(Remittance previous, RemittanceCreateRequest request) {
        BigDecimal requestedAmount = request.amountBrl().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        boolean sameRequest = previous.getReceiver().getId().equals(request.receiverId())
                && previous.getAmountBrl().compareTo(requestedAmount) == 0;

        if (!sameRequest) {
            throw new IdempotencyKeyReuseException(
                    "Idempotency-Key já foi usada em uma remessa com dados diferentes");
        }

        log.info("Idempotency-Key repetida: devolvendo a remessa {} já registrada, sem reprocessar",
                previous.getId());
        return RemittanceResponse.fromEntity(previous);
    }

    private Remittance saveRemittance(Remittance remittance) {
        try {
            return remittanceRepository.save(remittance);
        } catch (DataIntegrityViolationException e) {
            throw new IdempotencyKeyInUseException(
                    "Já existe uma remessa sendo processada com essa Idempotency-Key. Tente novamente em instantes.");
        }
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
                .referenceType(LedgerEntry.REFERENCE_TYPE_REMITTANCE)
                .referenceId(remittanceId)
                .build());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .wallet(receiverWallet)
                .currency(TARGET_CURRENCY)
                .type(LedgerEntryType.CREDIT)
                .amount(amountUsd)
                .referenceType(LedgerEntry.REFERENCE_TYPE_REMITTANCE)
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
