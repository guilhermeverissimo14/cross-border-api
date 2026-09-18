package com.guilherme.crossborderapi.service.dailylimit;

import com.guilherme.crossborderapi.enums.UserType;
import java.math.BigDecimal;

public interface DailyLimitPolicy {

    UserType getUserType();

    BigDecimal getDailyLimit();
}
