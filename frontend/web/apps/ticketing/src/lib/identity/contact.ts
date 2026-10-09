import {
  isValidPhoneNumber,
  parsePhoneNumberFromString,
  type CountryCode,
} from "libphonenumber-js";

/**
 * Client-side contact rules (same libphonenumber rules as the shared PhoneNumberInput).
 * identity-service is authoritative; this only avoids obvious round trips.
 */
export type ContactMode = "WHATSAPP" | "EMAIL";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export function isValidEmail(value: string): boolean {
  const v = value.trim();
  return v.length <= 254 && EMAIL_RE.test(v);
}

export function isValidWhatsApp(e164: string | undefined): boolean {
  if (!e164) return false;
  try {
    return isValidPhoneNumber(e164);
  } catch {
    return false;
  }
}

export function regionOf(e164: string | undefined): CountryCode | undefined {
  if (!e164) return undefined;
  return parsePhoneNumberFromString(e164)?.country;
}

/** Display mask, e.g. +260 97* ***123 / j***@gmail.com. Mirrors identity-service display masking. */
export function maskContact(mode: ContactMode, value: string): string {
  if (mode === "EMAIL") {
    const [local, domain = ""] = value.trim().split("@");
    return `${local.slice(0, 1)}***@${domain}`;
  }
  const p = parsePhoneNumberFromString(value);
  if (!p) return "***";
  const national = p.nationalNumber;
  const head = national.slice(0, 2);
  const tail = national.slice(-3);
  return `+${p.countryCallingCode} ${head}* ***${tail}`;
}
