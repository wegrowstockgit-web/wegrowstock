package com.invsys.core.security;

import com.invsys.domain.subscription.AppModule;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires the current tenant to have the given commercial module enabled
 * in {@code tenant_subscriptions.enabled_modules}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireModule {
    AppModule value();
}
