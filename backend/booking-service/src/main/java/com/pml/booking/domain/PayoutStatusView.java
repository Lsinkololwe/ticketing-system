package com.pml.booking.domain;

import com.pml.booking.domain.model.PayoutRequest;
import com.pml.shared.constants.PayoutRequestStatus;

/**
 * The status a payout shows. A hold overlays the stored status rather than replacing it (so lifting
 * it returns the request to exactly where it was), and while it is on the request reads
 * {@code ON_HOLD} to everyone who looks.
 */
public final class PayoutStatusView {

    public static final String ON_HOLD = "ON_HOLD";

    private PayoutStatusView() {
    }

    public static String of(PayoutRequest payout) {
        PayoutRequestStatus stored = payout.getStatus();
        if (payout.isOnHold() && (stored == null || !stored.isFinal() || stored == PayoutRequestStatus.FAILED)) {
            return ON_HOLD;
        }
        return stored == null ? null : stored.name();
    }
}
