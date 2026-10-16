export const PASSWORD_REQUIREMENTS_MESSAGE =
  "영문, 숫자, 특수문자를 포함해 8자 이상, UTF-8 기준 72바이트 이하로 입력해 주세요.";

export function meetsPasswordRequirements(password) {
  return (
    password.length >= 8 &&
    password.length <= 72 &&
    new Blob([password]).size <= 72 &&
    /[A-Za-z]/.test(password) &&
    /\d/.test(password) &&
    /[^A-Za-z0-9\s]/.test(password) &&
    !/\s/.test(password)
  );
}
