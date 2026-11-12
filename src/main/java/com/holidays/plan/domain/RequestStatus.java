package com.holidays.plan.domain;

import java.util.EnumSet;
import java.util.Set;

public enum RequestStatus {
    PENDING, APPROVED, REJECTED, WITHDRAWN, CANCEL_REQUESTED, CANCELLED;

    /** Stati in cui la persona risulta assente (e il saldo e' consumato). */
    public static final Set<RequestStatus> ACTIVE = EnumSet.of(APPROVED, CANCEL_REQUESTED);
    /** Stati che bloccano nuove richieste sovrapposte. */
    public static final Set<RequestStatus> BLOCKING = EnumSet.of(PENDING, APPROVED, CANCEL_REQUESTED);
}
