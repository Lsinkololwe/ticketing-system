package com.pml.identity.infrastructure.messaging;

import com.pml.shared.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for sending messages via WhatsApp Business API and Twilio SMS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagingService {

    private final WebClient.Builder webClientBuilder;

    @Value("${messaging.provider:whatsapp}")
    private String defaultProvider;

    // WhatsApp Business API Configuration
    @Value("${messaging.whatsapp.api-url:https://graph.facebook.com/v17.0}")
    private String whatsappApiUrl;

    @Value("${messaging.whatsapp.phone-number-id:}")
    private String whatsappPhoneNumberId;

    @Value("${messaging.whatsapp.access-token:}")
    private String whatsappAccessToken;

    @Value("${messaging.whatsapp.otp-template:otp_verification}")
    private String whatsappOtpTemplate;

    // Twilio Configuration (fallback for SMS)
    @Value("${messaging.twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${messaging.twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${messaging.twilio.from-number:}")
    private String twilioFromNumber;

    /**
     * Send OTP via the default channel (WhatsApp or SMS).
     *
     * @param phoneNumber The recipient's phone number
     * @param otp         The OTP code
     * @param channel     "whatsapp" or "sms"
     * @return Mono signaling completion
     */
    public Mono<Boolean> sendOtp(String phoneNumber, String otp, String channel) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);

        if ("whatsapp".equalsIgnoreCase(channel)) {
            return sendWhatsAppOtp(normalizedPhone, otp);
        } else if ("sms".equalsIgnoreCase(channel)) {
            return sendSmsOtp(normalizedPhone, otp);
        } else {
            // Default to WhatsApp, fallback to SMS
            return sendWhatsAppOtp(normalizedPhone, otp)
                    .onErrorResume(e -> {
                        log.warn("WhatsApp failed, falling back to SMS: {}", e.getMessage());
                        return sendSmsOtp(normalizedPhone, otp);
                    });
        }
    }

    /**
     * Send OTP via WhatsApp Business API.
     */
    private Mono<Boolean> sendWhatsAppOtp(String phoneNumber, String otp) {
        if (whatsappPhoneNumberId.isEmpty() || whatsappAccessToken.isEmpty()) {
            log.warn("WhatsApp not configured, skipping...");
            return Mono.just(false);
        }

        log.info("Sending WhatsApp OTP to: {}", maskPhoneNumber(phoneNumber));

        String url = whatsappApiUrl + "/" + whatsappPhoneNumberId + "/messages";

        // WhatsApp Cloud API message format
        Map<String, Object> message = new HashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", phoneNumber.replace("+", "")); // Remove + for WhatsApp API
        message.put("type", "template");

        // Template with OTP parameter
        Map<String, Object> template = new HashMap<>();
        template.put("name", whatsappOtpTemplate);
        template.put("language", Map.of("code", "en"));
        template.put("components", List.of(
                Map.of(
                        "type", "body",
                        "parameters", List.of(
                                Map.of("type", "text", "text", otp)
                        )
                )
        ));
        message.put("template", template);

        return webClientBuilder.build()
                .post()
                .uri(url)
                .header("Authorization", "Bearer " + whatsappAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(message)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    log.info("WhatsApp OTP sent successfully to: {}", maskPhoneNumber(phoneNumber));
                    return true;
                })
                .onErrorResume(e -> {
                    log.error("Failed to send WhatsApp OTP: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Send OTP via Twilio SMS.
     */
    private Mono<Boolean> sendSmsOtp(String phoneNumber, String otp) {
        if (twilioAccountSid.isEmpty() || twilioAuthToken.isEmpty() || twilioFromNumber.isEmpty()) {
            log.warn("Twilio not configured, skipping SMS...");
            return Mono.just(false);
        }

        log.info("Sending SMS OTP to: {}", maskPhoneNumber(phoneNumber));

        String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";
        String messageBody = "Your Event Ticketing verification code is: " + otp + ". Valid for 5 minutes.";

        return webClientBuilder.build()
                .post()
                .uri(url)
                .headers(h -> h.setBasicAuth(twilioAccountSid, twilioAuthToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("To=" + phoneNumber + "&From=" + twilioFromNumber + "&Body=" + messageBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    log.info("SMS OTP sent successfully to: {}", maskPhoneNumber(phoneNumber));
                    return true;
                })
                .onErrorResume(e -> {
                    log.error("Failed to send SMS OTP: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Send a general notification message.
     *
     * @param phoneNumber The recipient's phone number
     * @param message     The message content
     * @param channel     "whatsapp" or "sms"
     * @return Mono with success status
     */
    public Mono<Boolean> sendNotification(String phoneNumber, String message, String channel) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);

        if ("whatsapp".equalsIgnoreCase(channel)) {
            return sendWhatsAppText(normalizedPhone, message);
        } else {
            return sendSmsText(normalizedPhone, message);
        }
    }

    /**
     * Send a text message via WhatsApp.
     */
    private Mono<Boolean> sendWhatsAppText(String phoneNumber, String text) {
        if (whatsappPhoneNumberId.isEmpty() || whatsappAccessToken.isEmpty()) {
            return Mono.just(false);
        }

        String url = whatsappApiUrl + "/" + whatsappPhoneNumberId + "/messages";

        Map<String, Object> message = new HashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", phoneNumber.replace("+", ""));
        message.put("type", "text");
        message.put("text", Map.of("body", text));

        return webClientBuilder.build()
                .post()
                .uri(url)
                .header("Authorization", "Bearer " + whatsappAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(message)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> true)
                .onErrorResume(e -> {
                    log.error("Failed to send WhatsApp message: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Send a text message via SMS.
     */
    private Mono<Boolean> sendSmsText(String phoneNumber, String text) {
        if (twilioAccountSid.isEmpty() || twilioAuthToken.isEmpty() || twilioFromNumber.isEmpty()) {
            return Mono.just(false);
        }

        String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";

        return webClientBuilder.build()
                .post()
                .uri(url)
                .headers(h -> h.setBasicAuth(twilioAccountSid, twilioAuthToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("To=" + phoneNumber + "&From=" + twilioFromNumber + "&Body=" + text)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> true)
                .onErrorResume(e -> {
                    log.error("Failed to send SMS: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Normalize phone number to E.164 format.
     */
    private String normalizePhoneNumber(String phoneNumber) {
        // Canonical E.164 normalization (Google libphonenumber) so OTP/notification
        // delivery always targets a real MSISDN. Falls back to a digits-only form if
        // the number cannot be validated.
        String e164 = PhoneNumbers.toE164(phoneNumber);
        return e164 != null ? e164 : phoneNumber.replaceAll("[^0-9+]", "");
    }

    /**
     * Mask phone number for logging.
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber.length() <= 4) {
            return "****";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
