package com.pml.identity.domain.enums;

/**
 * Legal structure of the business entity.
 * Used for KYB (Know Your Business) verification.
 */
public enum BusinessType {
    /**
     * Individual operating as sole proprietor
     */
    SOLE_PROPRIETORSHIP,

    /**
     * Business partnership
     */
    PARTNERSHIP,

    /**
     * Limited liability company / corporation
     */
    LIMITED_COMPANY,

    /**
     * Non-governmental organization / Non-profit
     */
    NGO,

    /**
     * Government entity
     */
    GOVERNMENT,

    /**
     * Individual (not a registered business)
     */
    INDIVIDUAL
}
