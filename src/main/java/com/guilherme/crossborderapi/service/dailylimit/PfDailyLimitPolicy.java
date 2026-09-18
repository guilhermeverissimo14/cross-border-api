package com.guilherme.crossborderapi.service.dailylimit;

import com.guilherme.crossborderapi.enums.UserType;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class PfDailyLimitPolicy implements DailyLimitPolicy {

    private static final BigDecimal DAILY_LIMIT = new BigDecimal("10000.00");

    @Override
    public UserType getUserType() {
        return UserType.PF;
    }

    @Override
    public BigDecimal getDailyLimit() {
        return DAILY_LIMIT;
    }
}
