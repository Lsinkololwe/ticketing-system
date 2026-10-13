package com.pml.identity.auth.delivery;

import com.pml.identity.config.IdentityDeliveryProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The email that carries a sign-in code: escaped content and a send that is cancelled when the time budget ends. */
@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("the email provider escapes what it places in the message and stops sending when time runs out")
class EmailProviderTest {

    private static EmailProvider provider(JavaMailSender sender, Duration timeout) {
        IdentityDeliveryProperties properties = new IdentityDeliveryProperties();
        properties.getEmail().setEnabled(true);
        properties.getEmail().setFrom("no-reply@example.com");
        properties.getEmail().setTimeout(timeout);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mail = mock(ObjectProvider.class);
        when(mail.getIfAvailable()).thenReturn(sender);
        return new EmailProvider(properties, mail);
    }

    private static JavaMailSender senderCapturing(List<String> raw) {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            message.saveChanges();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            raw.add(out.toString(StandardCharsets.UTF_8));
            return null;
        }).when(sender).send(any(MimeMessage.class));
        return sender;
    }

    @Test
    @DisplayName("a hostile value is HTML-escaped in the HTML part, so markup and attribute breakouts are inert")
    void hostileValueIsEscaped() {
        String hostile = "<script>alert('x')</script>\"onmouseover=\"y&";

        String html = EmailProvider.html(hostile);

        assertThat(html).doesNotContain("<script>").doesNotContain("\"onmouseover=\"");
        assertThat(html).contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;&quot;onmouseover=&quot;y&amp;");
    }

    @Test
    @DisplayName("sent through the provider, the hostile value appears escaped in the delivered message")
    void hostileValueThroughTheProvider() {
        List<String> raw = new ArrayList<>();
        provider(senderCapturing(raw), Duration.ofSeconds(2))
                .send(DeliveryChannel.EMAIL, "person@example.com", "<img src=x onerror=alert(1)>").block();

        assertThat(raw).hasSize(1);
        assertThat(raw.get(0)).doesNotContain("<img src=x onerror=alert(1)></p>");
        assertThat(raw.get(0)).contains("&lt;img src=x onerror=alert(1)&gt;");
    }

    @Test
    @DisplayName("an ordinary six digit code is placed as it is")
    void digitsAreUnchanged() {
        assertThat(EmailProvider.html("482913")).contains(">482913<");
        assertThat(EmailProvider.text("482913")).contains("482913");
    }

    @Test
    @DisplayName("when the send outlives the budget the caller gets a delivery failure and the sending thread is interrupted")
    void timeoutCancelsTheSend() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> {
            started.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            } finally {
                finished.countDown();
            }
            return null;
        }).when(sender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> provider(sender, Duration.ofMillis(300))
                .send(DeliveryChannel.EMAIL, "person@example.com", "123456").block())
                .isInstanceOf(DeliveryFailedException.class);

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(finished.await(3, TimeUnit.SECONDS)).as("the send stopped instead of running on").isTrue();
        assertThat(interrupted).isTrue();
    }
}
