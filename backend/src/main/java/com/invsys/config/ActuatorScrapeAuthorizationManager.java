package com.invsys.config;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Grants anonymous scrape access to actuator endpoints only from configured VPC / Docker CIDRs.
 */
@Component
public class ActuatorScrapeAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final List<IpAddressMatcher> matchers;

    public ActuatorScrapeAuthorizationManager(ActuatorProperties properties) {
        List<IpAddressMatcher> built = new ArrayList<>();
        for (String cidr : properties.resolvedScrapeAllowedCidrs()) {
            built.add(new IpAddressMatcher(cidr));
        }
        this.matchers = List.copyOf(built);
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        String remote = context.getRequest().getRemoteAddr();
        if (remote == null || remote.isBlank()) {
            return new AuthorizationDecision(false);
        }
        for (IpAddressMatcher matcher : matchers) {
            if (matcher.matches(remote)) {
                return new AuthorizationDecision(true);
            }
        }
        return new AuthorizationDecision(false);
    }
}
