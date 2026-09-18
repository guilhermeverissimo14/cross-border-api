package com.guilherme.crossborderapi.service.dailylimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guilherme.crossborderapi.enums.UserType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyLimitPolicyProviderTest {

    private final DailyLimitPolicyProvider provider =
            new DailyLimitPolicyProvider(List.of(new PfDailyLimitPolicy(), new PjDailyLimitPolicy()));

    @Test
    void resolvesPfLimit() {
        assertThat(provider.getDailyLimit(UserType.PF)).isEqualByComparingTo("10000.00");
    }

    @Test
    void resolvesPjLimit() {
        assertThat(provider.getDailyLimit(UserType.PJ)).isEqualByComparingTo("50000.00");
    }

    @Test
    void throwsWhenNoPolicyRegisteredForUserType() {
        DailyLimitPolicyProvider emptyProvider = new DailyLimitPolicyProvider(List.of());

        assertThatThrownBy(() -> emptyProvider.getDailyLimit(UserType.PF))
                .isInstanceOf(IllegalStateException.class);
    }
}
