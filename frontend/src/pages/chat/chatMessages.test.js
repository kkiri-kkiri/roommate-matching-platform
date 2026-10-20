import { codePointLength, validMessage, mergeMessages } from "./chatMessages";

test.each(["가", "a", "😀"])("allows 2000 and rejects 2001 code points for %s", (character) => {
  expect(validMessage(character.repeat(2000))).toBe(true);
  expect(validMessage(character.repeat(2001))).toBe(false);
  expect(codePointLength(character.repeat(2000))).toBe(2000);
});

test.each(["가a😀", "👨‍👩‍👧‍👦", "é"])("counts mixed/compound text by code points: %s", (prefix) => {
  const text = prefix + "가".repeat(2000 - Array.from(prefix).length);
  expect(codePointLength(text)).toBe(2000);
  expect(validMessage(text)).toBe(true);
  expect(validMessage(text + "́")).toBe(false);
});

test("keeps blank validation and monotonic receipts, and orders sub-millisecond timestamps", () => {
  expect(validMessage("")).toBe(false); expect(validMessage(" \n\t")).toBe(false);
  const messages = mergeMessages([
    { id: 2, sentAt: "2026-10-19T12:00:00.123001", read: true },
    { id: 1, sentAt: "2026-10-19T12:00:00.123999", read: false },
  ], [{ id: 2, sentAt: "2026-10-19T12:00:00.123001", read: false }]);
  expect(messages.map((message) => message.id)).toEqual([2, 1]);
  expect(messages[0].read).toBe(true);
});
