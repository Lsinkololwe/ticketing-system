package com.pml.identity.auth.delivery;

import com.pml.identity.config.IdentityDeliveryProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Sends the sign-in code to a buyer's email address through {@code spring.mail.*}. The text and
 * HTML bodies are fixed templates into which only the code is placed, and it is HTML-escaped
 * there even though it is digits. Blocking JavaMail runs on the bounded-elastic scheduler and the
 * whole send is bounded by {@code identity.delivery.email.timeout}: when the budget runs out the
 * send is cancelled, which interrupts the sending thread, and the SMTP socket timeouts in
 * {@code spring.mail.properties} stop a connection that ignores the interrupt. Nothing sensitive
 * is logged.
 */
@Component
public class EmailProvider implements CodeDeliveryProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailProvider.class);

    static final String SUBJECT = "Your MyTicketZM sign-in code";

    private final IdentityDeliveryProperties.Email config;
    private final ObjectProvider<JavaMailSender> mail;

    public EmailProvider(IdentityDeliveryProperties properties, ObjectProvider<JavaMailSender> mail) {
        this.config = properties.getEmail();
        this.mail = mail;
    }

    @Override
    public boolean supports(DeliveryChannel channel) {
        return channel == DeliveryChannel.EMAIL;
    }

    @Override
    public boolean enabled() {
        return config.isEnabled() && mail.getIfAvailable() != null;
    }

    @Override
    public Mono<Void> send(DeliveryChannel channel, String recipient, String code) {
        return Mono.fromCallable(() -> {
                    JavaMailSender sender = mail.getIfAvailable();
                    if (sender == null) {
                        throw new IllegalStateException("no mail sender configured");
                    }
                    MimeMessage message = sender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                    helper.setFrom(config.getFrom());
                    helper.setTo(recipient);
                    helper.setSubject(SUBJECT);
                    helper.setText(text(code), html(code));
                    sender.send(message);
                    return Boolean.TRUE;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(config.getTimeout())
                .then()
                .onErrorMap(error -> {
                    log.warn("Email delivery failed: {}", error.getClass().getSimpleName());
                    return new DeliveryFailedException("email delivery failed");
                });
    }

    @Override
    public Mono<Void> sendNotice(DeliveryChannel channel, String recipient, ContactNotice notice) {
        return Mono.fromCallable(() -> {
                    JavaMailSender sender = mail.getIfAvailable();
                    if (sender == null) {
                        throw new IllegalStateException("no mail sender configured");
                    }
                    MimeMessage message = sender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                    helper.setFrom(config.getFrom());
                    helper.setTo(recipient);
                    helper.setSubject(notice.subject());
                    helper.setText(notice.text(), false);
                    sender.send(message);
                    return Boolean.TRUE;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(config.getTimeout())
                .then()
                .onErrorMap(error -> {
                    log.warn("Email notice failed: {}", error.getClass().getSimpleName());
                    return new DeliveryFailedException("email notice failed");
                });
    }

    /** Escapes the five characters that can open markup or leave an attribute, whatever the value. */
    static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    static String text(String code) {
        return "Your MyTicketZM sign-in code is " + code + ".\n\n"
                + "It expires in 5 minutes. If you did not ask for it, ignore this message.\n";
    }

    static String html(String code) {
        return "<!DOCTYPE html><html><body style=\"font-family:sans-serif\">"
                + "<p>Your MyTicketZM sign-in code is</p>"
                + "<p style=\"font-size:28px;letter-spacing:4px;font-weight:bold\">" + escapeHtml(code) + "</p>"
                + "<p>It expires in 5 minutes. If you did not ask for it, ignore this message.</p>"
                + "</body></html>";
    }
}
