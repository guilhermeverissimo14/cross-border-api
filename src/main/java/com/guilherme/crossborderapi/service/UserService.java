package com.guilherme.crossborderapi.service;

import com.guilherme.crossborderapi.dto.user.UserCreateRequest;
import com.guilherme.crossborderapi.dto.user.UserResponse;
import com.guilherme.crossborderapi.dto.wallet.UserWalletsResponse;
import com.guilherme.crossborderapi.dto.wallet.WalletDepositRequest;
import com.guilherme.crossborderapi.dto.wallet.WalletResponse;
import com.guilherme.crossborderapi.entity.User;
import com.guilherme.crossborderapi.entity.Wallet;
import com.guilherme.crossborderapi.enums.Currency;
import com.guilherme.crossborderapi.exception.DuplicateResourceException;
import com.guilherme.crossborderapi.exception.ResourceNotFoundException;
import com.guilherme.crossborderapi.repository.UserRepository;
import com.guilherme.crossborderapi.repository.WalletRepository;
import com.guilherme.crossborderapi.validation.InputNormalizer;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        // Normaliza antes de comparar: "Ana@X.com" e "ana@x.com", ou "123.456.789-09" e "12345678909", são o mesmo
        String email = InputNormalizer.email(request.email());
        String document = InputNormalizer.document(request.document());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("E-mail já cadastrado: " + email);
        }
        if (userRepository.existsByDocument(document)) {
            throw new DuplicateResourceException("Documento já cadastrado: " + document);
        }

        User user = User.builder()
                .fullName(request.fullName())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .userType(request.userType())
                .document(document)
                .build();

        User savedUser = userRepository.save(user);

        createWallet(savedUser, Currency.BRL);
        createWallet(savedUser, Currency.USD);

        log.info("Usuário criado: id={} tipo={}", savedUser.getId(), savedUser.getUserType());

        return UserResponse.fromEntity(savedUser);
    }

    private void createWallet(User user, Currency currency) {
        Wallet wallet = Wallet.builder()
                .user(user)
                .currency(currency)
                .balance(BigDecimal.ZERO)
                .build();
        walletRepository.save(wallet);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = findUserOrThrow(id);
        return UserResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserWalletsResponse getUserWallets(Long id) {
        findUserOrThrow(id);
        List<WalletResponse> balances = walletRepository.findByUserId(id).stream()
                .map(WalletResponse::fromEntity)
                .toList();
        return new UserWalletsResponse(id, balances);
    }

    @Transactional
    public UserWalletsResponse deposit(Long id, WalletDepositRequest request) {
        findUserOrThrow(id);

        Wallet wallet = walletRepository.findByUserIdAndCurrency(id, request.currency())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Carteira " + request.currency() + " não encontrada para o usuário id=" + id));

        wallet.setBalance(wallet.getBalance().add(request.amount()));
        walletRepository.save(wallet);

        log.info("Depósito realizado: userId={} moeda={} valor={}", id, request.currency(), request.amount());

        return getUserWallets(id);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: id=" + id));
    }
}
