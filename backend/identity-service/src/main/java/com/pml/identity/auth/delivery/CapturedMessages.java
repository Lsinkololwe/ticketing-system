package com.pml.identity.auth.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Messages the {@link CapturingProvider} kept instead of sending, for tests (profiles local and
 * test only). Holds the code in memory so a test can type it back; it never writes it anywhere.
 */
public final class CapturedMessages {

    /** One captured message. {@link #toString()} hides the recipient and the code. */
    public record Message(DeliveryChannel channel, String recipient, String code, Instant at) {
        @Override
        public String toString() {
            return "Message[" + channel + "]";
        }
    }

    /** One captured notice. */
    public record Notice(DeliveryChannel channel, String recipient, ContactNotice notice, Instant at) {
        @Override
        public String toString() {
            return "Notice[" + channel + ", " + notice + "]";
        }
    }

    private final List<Message> messages = new CopyOnWriteArrayList<>();
    private final List<Notice> notices = new CopyOnWriteArrayList<>();

    void addNotice(Notice notice) {
        notices.add(notice);
    }

    public List<Notice> notices() {
        return List.copyOf(notices);
    }

    public List<Notice> noticesTo(String recipient) {
        return notices.stream().filter(n -> n.recipient().equals(recipient)).toList();
    }

    void add(Message message) {
        messages.add(message);
    }

    public List<Message> all() {
        return List.copyOf(messages);
    }

    public Optional<Message> lastTo(String recipient) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).recipient().equals(recipient)) {
                return Optional.of(messages.get(i));
            }
        }
        return Optional.empty();
    }

    public Optional<String> lastCodeTo(String recipient) {
        return lastTo(recipient).map(Message::code);
    }

    public int countTo(String recipient) {
        return (int) messages.stream().filter(m -> m.recipient().equals(recipient)).count();
    }

    public void clear() {
        messages.clear();
        notices.clear();
    }

    @Override
    public String toString() {
        return "CapturedMessages[" + messages.size() + "]";
    }
}
