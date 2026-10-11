package com.crossborderpayment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables asynchronous e-mail sending (AuthMailSender) and the cleanup of old one-time codes.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
