/** Platform-wide buyer copy shown on every event page. Event-specific text always wins when the organizer supplied it. */
export const GENERAL_FAQ: Array<[string, string]> = [
  ['How do I get my tickets?', 'After you approve the mobile money payment, your tickets arrive by SMS and appear in My tickets with a QR code. Show it on your phone at the gate.'],
  ['Which payment methods can I use?', 'MTN Mobile Money, Airtel Money and Zamtel Kwacha. You approve a prompt on your phone with your PIN. We never ask for card details.'],
  ['Can I transfer my ticket to a friend?', "Yes. Use Transfer in My tickets with your friend's mobile number. They need a Showstop account and must accept. Your old QR code then stops working."],
];

export const DEFAULT_CANCELLATION =
  'If the organiser cancels the event, every ticket is refunded in full to the mobile money number that paid. If the event is rescheduled you keep your ticket, and you can request a refund if the change is material.';

export const DEFAULT_TERMS =
  "Entry requires a valid ticket with a QR code that has not been scanned before. Each ticket admits one person once, except where the tier says otherwise. Re-entry is at the organiser's discretion. The organiser may refuse entry for safety reasons. Tickets can be transferred to another Showstop user, but are not for resale above face value.";
