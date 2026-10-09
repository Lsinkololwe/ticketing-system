package com.pml.identity.auth.delivery;

/** A fixed, personal-data-free message telling a contact that something happened to the account it belongs to. */
public enum ContactNotice {
    CONTACT_ADDED("A contact was added to your MyTicketZM account.",
            "A new WhatsApp number or email address was added to your MyTicketZM account."),
    CONTACT_CHANGED("A contact on your MyTicketZM account was changed.",
            "A contact on your MyTicketZM account was changed."),
    CONTACT_REMOVED("A contact was removed from your MyTicketZM account.",
            "A contact was removed from your MyTicketZM account."),
    PRIMARY_CHANGED("The main contact of your MyTicketZM account changed.",
            "The main contact of your MyTicketZM account changed.");

    private final String subject;
    private final String text;

    ContactNotice(String subject, String text) {
        this.subject = subject;
        this.text = text;
    }

    public String subject() {
        return subject;
    }

    public String text() {
        return text + " If this was not you, contact support at once.";
    }
}
