import { meetsPasswordRequirements } from "./passwordValidation";

test("enforces BCrypt's UTF-8 byte limit without truncating passwords", () => {
  expect(meetsPasswordRequirements("Aa1!" + "a".repeat(68))).toBe(true);
  expect(meetsPasswordRequirements("Aa1!" + "a".repeat(69))).toBe(false);
  expect(meetsPasswordRequirements("Aa1!" + "가".repeat(22) + "aa")).toBe(true);
  expect(meetsPasswordRequirements("Aa1!" + "가".repeat(23))).toBe(false);
  expect(meetsPasswordRequirements("Aa1! 😀😀")).toBe(false);
});
