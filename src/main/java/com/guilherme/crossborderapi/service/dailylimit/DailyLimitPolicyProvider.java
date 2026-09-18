package com.guilherme.crossborderapi.service.dailylimit;

import com.guilherme.crossborderapi.enums.UserType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// O Spring injeta automaticamente TODOS os beans que implementam DailyLimitPolicy 
@Component
public class DailyLimitPolicyProvider {

    private final Map<UserType, DailyLimitPolicy> policiesByUserType;

    public DailyLimitPolicyProvider(List<DailyLimitPolicy> policies) {
        this.policiesByUserType = policies.stream()
                .collect(Collectors.toMap(DailyLimitPolicy::getUserType, Function.identity()));
    }

    public BigDecimal getDailyLimit(UserType userType) {
        DailyLimitPolicy policy = policiesByUserType.get(userType);
        if (policy == null) {
            throw new IllegalStateException("Nenhuma política de limite diário definida para " + userType);
        }
        return policy.getDailyLimit();
    }
}
