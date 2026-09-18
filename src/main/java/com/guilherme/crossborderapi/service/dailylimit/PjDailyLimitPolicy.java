package com.guilherme.crossborderapi.service.dailylimit;

import com.guilherme.crossborderapi.enums.UserType;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class PjDailyLimitPolicy implements DailyLimitPolicy {

    private static final BigDecimal DAILY_LIMIT = new BigDecimal("50000.00");

    @Override
    public UserType getUserType() {
        return UserType.PJ;
    }

    @Override
    public BigDecimal getDailyLimit() {
        return DAILY_LIMIT;
    }
}
