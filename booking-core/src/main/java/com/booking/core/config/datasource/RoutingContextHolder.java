package com.booking.core.config.datasource;

public class RoutingContextHolder {

    public static final ThreadLocal<Boolean> FORCE_PRIMARY = ThreadLocal.withInitial(() -> false);

    public static void setForcePrimary(boolean primary) {
        FORCE_PRIMARY.set(primary);
    }

    public static boolean isForcePrimary() {
        return Boolean.TRUE.equals(FORCE_PRIMARY.get());
    }

    public static void clear() {
        FORCE_PRIMARY.remove();
    }
}
