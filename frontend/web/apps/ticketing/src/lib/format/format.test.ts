import { describe, expect, it } from "vitest";
import {
  ago,
  clock,
  countdown,
  daysTo,
  fmtZmPhone,
  fullDate,
  hoursText,
  initials,
  mmss,
  money,
  parseZmMobile,
  shortDate,
} from "./index";

describe("format", () => {
  it("formats money with the Kwacha prefix", () => {
    expect(money(1500)).toBe("K 1,500");
    expect(money("12.5")).toBe("K 12.5");
    expect(money(null)).toBe("—");
  });
  it("renders dates in Zambian time (UTC+2)", () => {
    const t = "2026-11-14T15:00:00Z";
    expect(clock(t)).toBe("17:00");
    expect(fullDate(t)).toBe("Sat 14 Nov 2026");
    expect(shortDate("2026-11-14T23:30:00Z")).toBe("15 Nov");
  });
  it("counts down", () => {
    expect(countdown(125_000)).toBe("02:05");
    expect(countdown(3_725_000)).toBe("1h 2m 05s");
    expect(countdown(90_000_000)).toBe("1d 1h 0m");
    expect(mmss(9_100)).toBe("0:10");
    expect(
      daysTo("2026-11-14T00:00:00Z", Date.parse("2026-11-12T12:00:00Z")),
    ).toBe(2);
    expect(daysTo("2026-01-01T00:00:00Z")).toBe(0);
  });
  it("relative time and hours", () => {
    const now = Date.parse("2026-10-02T10:00:00Z");
    expect(ago(now - 20_000, now)).toBe("Just now");
    expect(ago(now - 3 * 3_600_000, now)).toBe("3 h ago");
    expect(hoursText(24)).toBe("24 hours");
    expect(hoursText(72)).toBe("3 days");
    expect(initials("Chanda Mwansa")).toBe("CM");
  });
  it("parses Zambian mobile numbers", () => {
    expect(parseZmMobile("097 123 4567")).toBe("971234567");
    expect(parseZmMobile("+260 96 1234567")).toBe("961234567");
    expect(parseZmMobile("123")).toBeNull();
    expect(fmtZmPhone("971234567")).toBe("+260 97 1234567");
  });
});
