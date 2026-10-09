package com.pml.identity.auth.delivery;

import com.pml.identity.config.IdentityDeliveryProperties;
import com.pml.identity.domain.enums.ContactType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Channel choice, email content, capture and the no-log rule (ET-IDN-001-R2, R4). */
@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001-R2/R4 · codes go out on the contact's own channel, never silently elsewhere, and are never logged")
class DeliveryOrchestratorTest {

    /** Records what it was asked to send and optionally fails. */
    private static final class Recording implements CodeDeliveryProvider {
        private final DeliveryChannel channel;
        private final boolean enabled;
        private final boolean fail;
        final List<String> recipients = new ArrayList<>();

        Recording(DeliveryChannel channel, boolean enabled, boolean fail) {
            this.channel = channel;
            this.enabled = enabled;
            this.fail = fail;
        }

        @Override
        public boolean supports(DeliveryChannel c) {
            return c == channel;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Mono<Void> send(DeliveryChannel c, String recipient, String code) {
            recipients.add(recipient);
            return fail ? Mono.error(new DeliveryFailedException("stub")) : Mono.empty();
        }
    }

    private static DeliveryOrchestrator orchestrator(CodeDeliveryProvider... providers) {
        return new DeliveryOrchestrator(List.of(providers), new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("a WhatsApp contact is delivered on WhatsApp and an email contact by email, and the channel used is returned")
    void nativeChannels() {
        var whatsapp = new Recording(DeliveryChannel.WHATSAPP, true, false);
        var email = new Recording(DeliveryChannel.EMAIL, true, false);
        var orchestrator = orchestrator(whatsapp, email);

        assertThat(orchestrator.deliver(ContactType.WHATSAPP, "+260971234567", "123456", null).block())
                .isEqualTo(DeliveryChannel.WHATSAPP);
        assertThat(orchestrator.deliver(ContactType.EMAIL, "a@example.com", "123456", null).block())
                .isEqualTo(DeliveryChannel.EMAIL);
        assertThat(whatsapp.recipients).containsExactly("+260971234567");
        assertThat(email.recipients).containsExactly("a@example.com");
    }

    @Test
    @DisplayName("a failed WhatsApp send is never redirected to email, even when email is preferred: the person did not give an address")
    void noSilentFallback() {
        var whatsapp = new Recording(DeliveryChannel.WHATSAPP, true, true);
        var email = new Recording(DeliveryChannel.EMAIL, true, false);
        var orchestrator = orchestrator(whatsapp, email);

        Throwable thrown = catchThrowable(() ->
                orchestrator.deliver(ContactType.WHATSAPP, "+260971234567", "123456", DeliveryChannel.EMAIL).block());
        assertThat(thrown).isInstanceOf(TranslatedRefusal.class);
        TranslatedRefusal refusal = (TranslatedRefusal) thrown;
        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.OTP_DELIVERY_FAILED);
        assertThat(refusal.retryable()).isTrue();
        assertThat(email.recipients).isEmpty();
    }

    @Test
    @DisplayName("a disabled channel is NOTIFICATION_CHANNEL_UNAVAILABLE before anything is sent")
    void channelUnavailable() {
        var orchestrator = orchestrator(new Recording(DeliveryChannel.WHATSAPP, false, false),
                new Recording(DeliveryChannel.EMAIL, true, false));
        assertThatThrownBy(() -> orchestrator.assertAvailable(ContactType.WHATSAPP))
                .isInstanceOfSatisfying(TranslatedRefusal.class,
                        r -> assertThat(r.errorCode()).isEqualTo(ErrorCode.NOTIFICATION_CHANNEL_UNAVAILABLE));
        orchestrator.assertAvailable(ContactType.EMAIL);
    }

    @Test
    @DisplayName("the capturing provider replaces the real ones, keeps the code in memory, and writes nothing to a log")
    void capture() {
        CapturedMessages captured = new CapturedMessages();
        var orchestrator = orchestrator(new Recording(DeliveryChannel.WHATSAPP, true, true),
                new CapturingProvider(captured, Clock.systemUTC()));
        try (LogWatch logs = new LogWatch()) {
            orchestrator.deliver(ContactType.WHATSAPP, "+260971234567", "246810", null).block();
            orchestrator.deliver(ContactType.EMAIL, "someone@example.com", "135791", null).block();
            logs.assertNoneContains("246810", "135791", "260971234567", "someone@example.com");
        }
        assertThat(captured.lastCodeTo("+260971234567")).contains("246810");
        assertThat(captured.lastCodeTo("someone@example.com")).contains("135791");
        assertThat(captured.all().toString()).doesNotContain("246810").doesNotContain("someone");
    }

    private static EmailProvider emailProvider(JavaMailSender sender, Duration timeout) {
        IdentityDeliveryProperties properties = new IdentityDeliveryProperties();
        properties.getEmail().setEnabled(true);
        properties.getEmail().setFrom("no-reply@myticketzm.test");
        properties.getEmail().setTimeout(timeout);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return new EmailProvider(properties, provider);
    }

    @Test
    @DisplayName("the email carries the fixed subject, from address, and the code in both the text and HTML parts")
    void emailContent() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));

        emailProvider(sender, Duration.ofSeconds(2)).send(DeliveryChannel.EMAIL, "buyer@example.com", "862047").block();

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        String mime = raw.toString(StandardCharsets.UTF_8);
        assertThat(message.getSubject()).isEqualTo(EmailProvider.SUBJECT);
        assertThat(message.getFrom()[0].toString()).isEqualTo("no-reply@myticketzm.test");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("buyer@example.com");
        assertThat(mime).contains("text/plain").contains("text/html");
        assertThat(mime.split("862047", -1).length - 1).as("code in both parts").isEqualTo(2);
        assertThat(EmailProvider.html("862047")).doesNotContain("buyer@example.com");
    }

    @Test
    @DisplayName("a mail failure or a hung mail server is a delivery failure that logs neither the address nor the code")
    void emailFailureAndTimeout() {
        JavaMailSender failing = mock(JavaMailSender.class);
        when(failing.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(i -> {
            throw new MailSendException("550 rejected buyer@example.com 862047");
        }).when(failing).send(any(MimeMessage.class));

        JavaMailSender hanging = mock(JavaMailSender.class);
        when(hanging.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(i -> {
            Thread.sleep(2_000);
            return null;
        }).when(hanging).send(any(MimeMessage.class));

        try (LogWatch logs = new LogWatch()) {
            assertThatThrownBy(() -> emailProvider(failing, Duration.ofSeconds(2))
                    .send(DeliveryChannel.EMAIL, "buyer@example.com", "862047").block())
                    .isInstanceOf(DeliveryFailedException.class)
                    .hasMessageNotContaining("buyer").hasMessageNotContaining("862047");
            assertThatThrownBy(() -> emailProvider(hanging, Duration.ofMillis(200))
                    .send(DeliveryChannel.EMAIL, "buyer@example.com", "862047").block())
                    .isInstanceOf(DeliveryFailedException.class);
            logs.assertNoneContains("862047", "buyer@example.com");
        }
    }
}
