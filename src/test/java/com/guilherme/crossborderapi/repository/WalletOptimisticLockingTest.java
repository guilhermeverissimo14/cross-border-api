package com.guilherme.crossborderapi.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guilherme.crossborderapi.entity.User;
import com.guilherme.crossborderapi.entity.Wallet;
import com.guilherme.crossborderapi.enums.Currency;
import com.guilherme.crossborderapi.enums.UserType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

// Prova, com o banco real, que duas "sessões" atualizando a mesma carteira colidem —
// não é só teoria do @Version, é o comportamento real do Hibernate + H2 nesse projeto.
@SpringBootTest
class WalletOptimisticLockingTest {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void secondStaleUpdateThrowsOptimisticLockingException() {
        User user = userRepository.saveAndFlush(User.builder()
                .fullName("Teste Concorrência")
                .email("concorrencia@teste.com")
                .password("123456")
                .userType(UserType.PF)
                .document("00000000000")
                .build());

        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder()
                .user(user)
                .currency(Currency.BRL)
                .balance(BigDecimal.TEN)
                .build());

        // "sessão A": lê a carteira (version 0), atualiza e salva primeiro sucesso, vira version 1
        wallet.setBalance(new BigDecimal("20"));
        walletRepository.saveAndFlush(wallet);

        // "sessão B": tinha lido a carteira ANTES da sessão A salvar ainda acha que a version é 0
        Wallet staleWallet = Wallet.builder()
                .id(wallet.getId())
                .user(user)
                .currency(Currency.BRL)
                .balance(new BigDecimal("30"))
                .build();
        staleWallet.setVersion(0L);

        assertThatThrownBy(() -> walletRepository.saveAndFlush(staleWallet))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
