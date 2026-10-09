package com.pml.identity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** {@code identity.delivery.*}: how codes reach people. */
@Data
@Component
@ConfigurationProperties(prefix = "identity.delivery")
public class IdentityDeliveryProperties {

    private Whatsapp whatsapp = new Whatsapp();
    private Email email = new Email();
    private Capture capture = new Capture();

    @Data
    public static class Whatsapp {
        private boolean enabled = false;
        private String apiUrl;
        private String phoneNumberId;
        private String accessToken;
        private String templateName;
        private String templateLanguage = "en";
        /** Prefix of the parameterless notice templates ({prefix}_contact_added, ...); unset sends no WhatsApp notices. */
        private String noticeTemplateName;
        private Duration timeout = Duration.ofSeconds(5);
    }

    @Data
    public static class Email {
        private boolean enabled = false;
        private String from;
        private Duration timeout = Duration.ofSeconds(5);
    }

    /** In-memory capture of sent messages for tests. Allowed in profiles local and test only. It never logs a code. */
    @Data
    public static class Capture {
        private boolean enabled = false;
    }
}
