// Payment types for the ticketing application

export enum PaymentMethod {
  MOBILE_MONEY = 'MOBILE_MONEY',
  CARD = 'CARD',
  BANK_TRANSFER = 'BANK_TRANSFER',
}

export enum ZambianMobileProvider {
  MTN = 'MTN',
  AIRTEL = 'AIRTEL',
  ZAMTEL = 'ZAMTEL',
}

export interface PaymentMethodInfo {
  name: string;
  description: string;
  icon: string;
  feeRate: number;
  isPopular?: boolean;
}

export interface MobileProviderInfo {
  name: string;
  color: string;
  prefix: string[];
}

export interface PaymentConfig {
  currency: string;
  minAmount: number;
  maxAmount: number;
}

export interface TicketPurchaseData {
  eventId: string;
  ticketCategoryId: string;
  quantity: number;
  buyerName: string;
  buyerEmail: string;
  buyerPhone: string;
  paymentMethod: PaymentMethod;
  phoneNumber?: string;
  mobileProvider?: ZambianMobileProvider;
  cardDetails?: {
    number: string;
    expiry: string;
    cvv: string;
    name: string;
  };
  metadata?: Record<string, any>;
}

export interface PurchaseResult {
  success: boolean;
  transactionId?: string;
  error?: string;
}
