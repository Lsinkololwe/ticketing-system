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
 * Sends notification messages by WhatsApp Business API text or email. Codes are sent by
 * {@code com.pml.identity.auth.delivery}, not here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagingService {

    private final WebClient.Builder webClientBuilder;

    /** Absent unless {@code spring.mail.*} is configured; email notifications then report "not accepted". */
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.mail.javamail.JavaMailSender> mailSender;

    @Value("${identity.delivery.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${identity.delivery.email.from:}")
    private String emailFrom;

    // WhatsApp Business API Configuration
    @Value("${messaging.whatsapp.api-url:https://graph.facebook.com/v17.0}")
    private String whatsappApiUrl;

    @Value("${messaging.whatsapp.phone-number-id:}")
    private String whatsappPhoneNumberId;

    @Value("${messaging.whatsapp.access-token:}")
    private String whatsappAccessToken;

    /**
     * Send a general notification message.
     *
     * @param phoneNumber The recipient's phone number
     * @param message     The message content
     * @param channel     "whatsapp" or "email"
     * @return Mono with success status
     */
    public Mono<Boolean> sendNotification(String destination, String message, String channel) {
        if ("email".equalsIgnoreCase(channel)) {
            return sendEmail(destination, message);
        }
        String normalizedPhone = normalizePhoneNumber(destination);

        if ("whatsapp".equalsIgnoreCase(channel)) {
            return sendWhatsAppText(normalizedPhone, message);
        }
        // SMS is not a channel (D-39); other channels have no destination and never get here.
        return Mono.just(false);
    }

    /**
     * Send a notification by email. The address is the recipient's verified email contact; it is
     * handed to the mail sender and never logged.
     */
    private Mono<Boolean> sendEmail(String address, String text) {
        org.springframework.mail.javamail.JavaMailSender sender = mailSender.getIfAvailable();
        if (!emailEnabled || sender == null || emailFrom == null || emailFrom.isBlank()) {
            return Mono.just(false);
        }
        return Mono.fromCallable(() -> {
                    org.springframework.mail.SimpleMailMessage mail = new org.springframework.mail.SimpleMailMessage();
                    mail.setFrom(emailFrom);
                    mail.setTo(address);
                    mail.setSubject("MyTicketZM");
                    mail.setText(text);
                    sender.send(mail);
                    return true;
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Failed to send email notification: {}", e.getClass().getSimpleName());
                    return Mono.just(false);
                });
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
     * Normalize phone number to E.164 format.
     */
    private String normalizePhoneNumber(String phoneNumber) {
        // Canonical E.164 normalization (Google libphonenumber) so OTP/notification
        // delivery always targets a real MSISDN. Falls back to a digits-only form if
        // the number cannot be validated.
        String e164 = PhoneNumbers.toE164(phoneNumber);
        return e164 != null ? e164 : phoneNumber.replaceAll("[^0-9+]", "");
    }
}
