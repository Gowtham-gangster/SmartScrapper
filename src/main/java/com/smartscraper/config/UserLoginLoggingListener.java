package com.smartscraper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class UserLoginLoggingListener implements ApplicationListener<InteractiveAuthenticationSuccessEvent> {

    private static final Logger logger = LoggerFactory.getLogger(UserLoginLoggingListener.class);

    @Override
    public void onApplicationEvent(InteractiveAuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        String username = auth != null ? auth.getName() : "unknown";
        String authType = auth != null && auth.getClass() != null ? auth.getClass().getSimpleName() : "unknown";

        logger.info("User login success: username={} authType={}", username, authType);
    }
}

