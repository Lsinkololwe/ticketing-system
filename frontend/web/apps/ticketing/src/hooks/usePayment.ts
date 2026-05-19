'use client';

import { useState } from 'react';
import {
  PaymentMethod,
  ZambianMobileProvider,
  PaymentMethodInfo,
  MobileProviderInfo,
  PaymentConfig,
  TicketPurchaseData,
  PurchaseResult,
} from '@/types/payment';

const PAYMENT_CONFIG: PaymentConfig = {
  currency: 'ZMW',
  minAmount: 1,
  maxAmount: 100000,
};

const PAYMENT_METHODS: Record<PaymentMethod, PaymentMethodInfo> = {
  [PaymentMethod.MOBILE_MONEY]: {
    name: 'Mobile Money',
    description: 'Pay with MTN, Airtel, or Zamtel Money',
    icon: '📱',
    feeRate: 0.02,
    isPopular: true,
  },
  [PaymentMethod.CARD]: {
    name: 'Card Payment',
    description: 'Pay with Visa or Mastercard',
    icon: '💳',
    feeRate: 0.03,
  },
  [PaymentMethod.BANK_TRANSFER]: {
    name: 'Bank Transfer',
    description: 'Pay directly from your bank account',
    icon: '🏦',
    feeRate: 0.01,
  },
};

const MOBILE_PROVIDERS: Record<ZambianMobileProvider, MobileProviderInfo> = {
  [ZambianMobileProvider.MTN]: {
    name: 'MTN Mobile Money',
    color: '#FFCC00',
    prefix: ['096', '076'],
  },
  [ZambianMobileProvider.AIRTEL]: {
    name: 'Airtel Money',
    color: '#FF0000',
    prefix: ['097', '077'],
  },
  [ZambianMobileProvider.ZAMTEL]: {
    name: 'Zamtel Kwacha',
    color: '#00A651',
    prefix: ['095', '075'],
  },
};

export function usePayment() {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const calculateFees = (amount: number, method: PaymentMethod): number => {
    const methodInfo = PAYMENT_METHODS[method];
    return Math.round(amount * methodInfo.feeRate * 100) / 100;
  };

  const validatePhoneNumber = (phone: string, provider: ZambianMobileProvider): boolean => {
    const providerInfo = MOBILE_PROVIDERS[provider];
    const cleanPhone = phone.replace(/\D/g, '');

    if (cleanPhone.length !== 10) return false;

    return providerInfo.prefix.some(prefix => cleanPhone.startsWith(prefix));
  };

  const formatPhoneNumber = (phone: string, provider: ZambianMobileProvider): string => {
    const cleanPhone = phone.replace(/\D/g, '');
    return `+260 ${cleanPhone.slice(0, 3)} ${cleanPhone.slice(3, 6)} ${cleanPhone.slice(6)}`;
  };

  const purchaseTicket = async (data: TicketPurchaseData): Promise<PurchaseResult> => {
    setIsLoading(true);
    setError(null);

    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 2000));

      // In a real implementation, this would call the payment gateway
      console.log('Processing purchase:', data);

      return {
        success: true,
        transactionId: `TXN-${Date.now()}`,
      };
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Payment failed';
      setError(errorMessage);
      return {
        success: false,
        error: errorMessage,
      };
    } finally {
      setIsLoading(false);
    }
  };

  return {
    purchaseTicket,
    isLoading,
    error,
    calculateFees,
    validatePhoneNumber,
    formatPhoneNumber,
    paymentConfig: PAYMENT_CONFIG,
  };
}

export function usePaymentMethods() {
  const getPaymentMethodInfo = (method: PaymentMethod): PaymentMethodInfo => {
    return PAYMENT_METHODS[method];
  };

  const getMobileProviderInfo = (provider: ZambianMobileProvider): MobileProviderInfo => {
    return MOBILE_PROVIDERS[provider];
  };

  return {
    getPaymentMethodInfo,
    getMobileProviderInfo,
    paymentMethods: Object.values(PaymentMethod),
    mobileProviders: Object.values(ZambianMobileProvider),
  };
}
