package com.invsys.admin.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a control-plane mutation for append-only {@code platform_audit_logs} recording.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformAudit {
    String action();

    /** Method parameter name holding the target tenant UUID (optional). */
    String tenantIdParam() default "";

    /** SOC 2 actor classification written to {@code platform_audit_logs.actor_type}. */
    String actorType() default "PLATFORM_ADMIN";
}
