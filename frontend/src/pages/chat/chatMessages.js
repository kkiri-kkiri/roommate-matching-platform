export const MAX_MESSAGE_LENGTH = 2000;
export const codePointLength = (text) => Array.from(text).length;
export const validMessage = (text) => text.trim().length > 0 && codePointLength(text) <= MAX_MESSAGE_LENGTH;

const timestamp = (text = "") => {
  const [seconds, fraction = ""] = text.split(".");
  return `${seconds}.${fraction.padEnd(9, "0")}`;
};
export function compareMessages(a, b) {
  const left = timestamp(a.sentAt), right = timestamp(b.sentAt);
  return left < right ? -1 : left > right ? 1 : Number(a.id) - Number(b.id);
}
export function mergeMessages(...groups) {
  const byId = new Map();
  for (const item of groups.flat()) {
    const previous = byId.get(String(item.id));
    byId.set(String(item.id), { ...previous, ...item, read: Boolean(previous?.read || item.read) });
  }
  return [...byId.values()].sort(compareMessages);
}
export function mapMessage(item, counterpartId) {
  return { ...item, sender: item.senderType ? (item.senderType === "ME" ? "me" : "other")
    : (String(item.senderId) === String(counterpartId) ? "other" : "me") };
}
