package com.ria.olita.tech.silingan.service.sms;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ria.olita.tech.silingan.config.SmsProperties;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * SMS Service that manages multiple SMS providers.
 * Selects the appropriate provider based on configuration.
 */
@Service
@RequiredArgsConstructor
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final SmsProperties smsProperties;
    private final List<SmsProvider> providers;
    private Map<String, SmsProvider> providerMap;
	/**
	 * -- GETTER --
	 *  Gets the currently active SMS provider.
	 *
	 * @return the active provider, or null if none available
	 */
	@Getter
	private SmsProvider activeProvider;

    @PostConstruct
    void init() {
        // Create a map of providers by name (lowercase for case-insensitive matching)
        providerMap = providers.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toLowerCase(),
                        Function.identity()
                ));

        // Set the active provider based on configuration
        String configuredProvider = smsProperties.getProvider().toLowerCase();
        activeProvider = providerMap.get(configuredProvider);

        if (activeProvider == null) {
            log.warn("Configured SMS provider '{}' not found. Available providers: {}",
                    smsProperties.getProvider(),
                    providerMap.keySet());
            // Fall back to first available provider
            activeProvider = providers.isEmpty() ? null : providers.getFirst();
        }

        if (activeProvider != null) {
            log.info("SMS Service initialized with provider: {} (configured: {})",
                    activeProvider.getProviderName(),
                    activeProvider.isConfigured() ? "yes" : "no");
            if (smsProperties.isBypassSending()) {
                log.warn("SMS bypass mode is ENABLED. Outbound SMS sending will be skipped.");
            }
        } else {
            log.warn("No SMS providers available. SMS functionality will be disabled.");
        }
    }

    /**
     * Sends an OTP to the specified phone number using the active provider.
     *
     * @param phoneNumber the recipient's phone number
     * @param otp         the OTP to send
     * @return true if sent successfully
     */
    public boolean sendOtp(String phoneNumber, String otp) {
        if (smsProperties.isBypassSending()) {
            log.warn("SMS bypass is enabled; skipping OTP send to {}", maskPhoneNumber(phoneNumber));
            return true;
        }

        if (activeProvider == null) {
            log.error("No SMS provider available to send OTP");
            return false;
        }

        log.debug("Sending OTP to {} via {}", maskPhoneNumber(phoneNumber), activeProvider.getProviderName());
        return activeProvider.sendOtp(phoneNumber, otp);
    }

	/**
	 * Sends a generic SMS message to the specified phone number using the active provider.
	 *
	 * @param phoneNumber the recipient's phone number
	 * @param message     the message to send
	 * @return true if sent successfully
	 */
	public boolean sendSms(String phoneNumber, String message) {
		if (smsProperties.isBypassSending()) {
			log.warn("SMS bypass is enabled; skipping SMS send to {}", maskPhoneNumber(phoneNumber));
			return true;
		}

		if (activeProvider == null) {
			log.error("No SMS provider available to send SMS");
			return false;
		}

		log.debug("Sending SMS to {} via {}", maskPhoneNumber(phoneNumber), activeProvider.getProviderName());
		return activeProvider.sendSms(phoneNumber, message);
	}

    /**
     * Masks a phone number for logging purposes.
     * Example: +639171234567 -> +6391****4567
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) {
            return "****";
        }
        int len = phoneNumber.length();
        return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(len - 4);
    }
}

