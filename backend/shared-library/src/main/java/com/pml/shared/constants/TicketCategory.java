package com.pml.shared.constants;

import java.math.BigDecimal;

/**
 * Ticket Category Enum
 */
public enum TicketCategory {
    GENERAL("GENERAL", "General Admission", false, false),
    PRE_SALE("PRE_SALE", "Pre-Sale", false, true),
    VIP("VIP", "VIP", true, false),
    VVIP("VVIP", "VVIP", true, false),
    PREMIUM("PREMIUM", "Premium", true, false),
    EARLY_BIRD("EARLY_BIRD", "Early Bird", false, true),
    STUDENT("STUDENT", "Student", false, false),
    SENIOR("SENIOR", "Senior", false, false),
    GROUP("GROUP", "Group", false, false),
    CORPORATE("CORPORATE", "Corporate", false, false),
    SPONSOR("SPONSOR", "Sponsor", true, false),
    FREE("FREE", "Free", false, false);

    private final String code;
    private final String displayName;
    private final boolean premium;
    private final boolean preSale;

    TicketCategory(String code, String displayName, boolean premium, boolean preSale) {
        this.code = code;
        this.displayName = displayName;
        this.premium = premium;
        this.preSale = preSale;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public boolean isPremium() { return premium; }
    public boolean isPreSale() { return preSale; }

    public static TicketCategory fromCode(String code) {
        for (TicketCategory category : values()) {
            if (category.code.equals(code)) {
                return category;
            }
        }
        return null;
    }
}
