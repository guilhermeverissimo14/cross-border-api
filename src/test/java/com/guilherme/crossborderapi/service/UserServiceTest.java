package com.guilherme.crossborderapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guilherme.crossborderapi.dto.user.UserCreateRequest;
import com.guilherme.crossborderapi.dto.user.UserResponse;
import com.guilherme.crossborderapi.dto.wallet.UserWalletsResponse;
import com.guilherme.crossborderapi.dto.wallet.WalletDepositRequest;
import com.guilherme.crossborderapi.entity.User;
import com.guilherme.crossborderapi.entity.Wallet;
import com.guilherme.crossborderapi.enums.Currency;
import com.guilherme.crossborderapi.enums.UserType;
import com.guilherme.crossborderapi.exception.DuplicateResourceException;
import com.guilherme.crossborderapi.exception.ResourceNotFoundException;
import com.guilherme.crossborderapi.repository.UserRepository;
import com.guilherme.crossborderapi.repository.WalletRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private final UserCreateRequest request =
            new UserCreateRequest("Maria", "maria@email.com", "senha123", UserType.PF, "12345678900");

    @Test
    void createUserHashesPasswordAndCreatesBrlAndUsdWallets() {
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByDocument(request.document())).thenReturn(false);
        when(passwordEncoder.encode("senha123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(request);

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertThat(user.getValue().getPassword()).isEqualTo("hash");
        assertThat(response.email()).isEqualTo("maria@email.com");
        assertThat(response.userType()).isEqualTo(UserType.PF);

        ArgumentCaptor<Wallet> wallets = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository, times(2)).save(wallets.capture());
        assertThat(wallets.getAllValues()).extracting(Wallet::getCurrency)
                .containsExactly(Currency.BRL, Currency.USD);
        assertThat(wallets.getAllValues()).allSatisfy(wallet ->
                assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    void createUserRejectsDuplicateEmail() {
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("maria@email.com");

        verify(userRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void createUserRejectsDuplicateDocument() {
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByDocument(request.document())).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("12345678900");

        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserByIdThrowsWhenMissing() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado: id=9");
    }

    @Test
    void getAllUsersMapsEveryUser() {
        when(userRepository.findAll()).thenReturn(List.of(
                User.builder().id(1L).email("a@a.com").userType(UserType.PF).build(),
                User.builder().id(2L).email("b@b.com").userType(UserType.PJ).build()));

        assertThat(userService.getAllUsers()).extracting(UserResponse::email)
                .containsExactly("a@a.com", "b@b.com");
    }

    @Test
    void getUserWalletsReturnsBalancesPerCurrency() {
        User user = User.builder().id(1L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(1L)).thenReturn(List.of(
                wallet(user, Currency.BRL, "10.00"), wallet(user, Currency.USD, "2.00")));

        UserWalletsResponse response = userService.getUserWallets(1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.balances()).hasSize(2);
        assertThat(response.balances().get(0).currency()).isEqualTo(Currency.BRL);
        assertThat(response.balances().get(1).balance()).isEqualByComparingTo("2.00");
    }

    @Test
    void depositAddsToTheBalanceOfTheRequestedCurrency() {
        User user = User.builder().id(1L).build();
        Wallet brl = wallet(user, Currency.BRL, "10.00");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserIdAndCurrency(1L, Currency.BRL)).thenReturn(Optional.of(brl));
        when(walletRepository.findByUserId(1L)).thenReturn(List.of(brl));

        userService.deposit(1L, new WalletDepositRequest(Currency.BRL, new BigDecimal("5.50")));

        assertThat(brl.getBalance()).isEqualByComparingTo("15.50");
        verify(walletRepository).save(brl);
    }

    @Test
    void depositThrowsWhenUserOrWalletIsMissing() {
        WalletDepositRequest deposit = new WalletDepositRequest(Currency.USD, BigDecimal.TEN);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.deposit(1L, deposit)).isInstanceOf(ResourceNotFoundException.class);

        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
        when(walletRepository.findByUserIdAndCurrency(2L, Currency.USD)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.deposit(2L, deposit))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Carteira USD");

        verify(walletRepository, never()).save(any());
    }

    private Wallet wallet(User user, Currency currency, String balance) {
        return Wallet.builder().user(user).currency(currency).balance(new BigDecimal(balance)).build();
    }
}
