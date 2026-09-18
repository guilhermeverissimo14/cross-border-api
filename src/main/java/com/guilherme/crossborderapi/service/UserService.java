package com.guilherme.crossborderapi.service;

import com.guilherme.crossborderapi.dto.user.UserCreateRequest;
import com.guilherme.crossborderapi.dto.user.UserResponse;
import com.guilherme.crossborderapi.entity.User;
import com.guilherme.crossborderapi.entity.Wallet;
import com.guilherme.crossborderapi.enums.Currency;
import com.guilherme.crossborderapi.exception.DuplicateResourceException;
import com.guilherme.crossborderapi.repository.UserRepository;
import com.guilherme.crossborderapi.repository.WalletRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("E-mail já cadastrado: " + request.email());
        }
        if (userRepository.existsByDocument(request.document())) {
            throw new DuplicateResourceException("Documento já cadastrado: " + request.document());
        }

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .userType(request.userType())
                .document(request.document())
                .build();

        User savedUser = userRepository.save(user);

        createWallet(savedUser, Currency.BRL);
        createWallet(savedUser, Currency.USD);

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
}
