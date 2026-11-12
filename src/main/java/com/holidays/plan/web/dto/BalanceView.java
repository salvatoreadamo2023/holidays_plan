package com.holidays.plan.web.dto;

import java.math.BigDecimal;

public record BalanceView(int year,
                          BigDecimal leaveTotal, BigDecimal leaveUsed, BigDecimal leavePending, BigDecimal leaveAvailable,
                          BigDecimal permitTotal, BigDecimal permitUsed, BigDecimal permitPending, BigDecimal permitAvailable,
                          BigDecimal sickDays) {}
