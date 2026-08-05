const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function isValidEmail(email: string): boolean {
  return EMAIL_REGEX.test(email.trim());
}

export interface PasswordRule {
  key: string;
  label: string;
  test: (password: string) => boolean;
}

// Kept in sync with the backend's @ValidPassword check (RegisterRequest.java) — the
// server is the real enforcement point, this is just so the user sees the same rules
// live instead of finding out only after submitting.
export const passwordRules: PasswordRule[] = [
  { key: 'length', label: 'At least 10 characters', test: (p) => p.length >= 10 },
  { key: 'uppercase', label: 'One uppercase letter', test: (p) => /[A-Z]/.test(p) },
  { key: 'number', label: 'One number', test: (p) => /[0-9]/.test(p) },
  { key: 'symbol', label: 'One symbol (e.g. ! @ # $ %)', test: (p) => /[^A-Za-z0-9]/.test(p) },
];

export function isPasswordValid(password: string): boolean {
  return passwordRules.every((rule) => rule.test(password));
}
