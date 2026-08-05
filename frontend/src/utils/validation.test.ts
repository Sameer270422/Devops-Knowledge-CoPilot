import { describe, expect, it } from 'vitest';
import { isPasswordValid, isValidEmail, passwordRules } from './validation';

describe('isValidEmail', () => {
  it('accepts well-formed addresses', () => {
    expect(isValidEmail('user@example.com')).toBe(true);
    expect(isValidEmail('first.last+tag@sub.example.co')).toBe(true);
  });

  it('rejects addresses missing an @ or a domain dot', () => {
    expect(isValidEmail('not-an-email')).toBe(false);
    expect(isValidEmail('missing-domain@')).toBe(false);
    expect(isValidEmail('@missing-local.com')).toBe(false);
    expect(isValidEmail('no-dot@example')).toBe(false);
  });

  it('rejects addresses containing whitespace', () => {
    expect(isValidEmail('has space@example.com')).toBe(false);
  });

  it('trims surrounding whitespace before validating', () => {
    expect(isValidEmail('  user@example.com  ')).toBe(true);
  });

  it('rejects an empty string', () => {
    expect(isValidEmail('')).toBe(false);
  });
});

describe('passwordRules', () => {
  it('defines exactly the four rules the backend enforces (length, uppercase, number, symbol)', () => {
    const keys = passwordRules.map((r) => r.key).sort();
    expect(keys).toEqual(['length', 'number', 'symbol', 'uppercase']);
  });

  it('length rule requires at least 10 characters', () => {
    const rule = passwordRules.find((r) => r.key === 'length')!;
    expect(rule.test('Sh0rt!12')).toBe(false); // 8 chars
    expect(rule.test('LongEnough1!')).toBe(true); // 12 chars
  });

  it('uppercase rule requires at least one capital letter', () => {
    const rule = passwordRules.find((r) => r.key === 'uppercase')!;
    expect(rule.test('lowercase1!')).toBe(false);
    expect(rule.test('Uppercase1!')).toBe(true);
  });

  it('number rule requires at least one digit', () => {
    const rule = passwordRules.find((r) => r.key === 'number')!;
    expect(rule.test('NoDigitsHere!')).toBe(false);
    expect(rule.test('HasDigit1!')).toBe(true);
  });

  it('symbol rule requires at least one non-alphanumeric character', () => {
    const rule = passwordRules.find((r) => r.key === 'symbol')!;
    expect(rule.test('NoSymbolHere1')).toBe(false);
    expect(rule.test('HasSymbol1!')).toBe(true);
  });
});

describe('isPasswordValid', () => {
  it('rejects a password failing any single rule', () => {
    expect(isPasswordValid('short1!')).toBe(false); // too short
    expect(isPasswordValid('nouppercase1!')).toBe(false); // no uppercase
    expect(isPasswordValid('NoNumbers!!')).toBe(false); // no digit
    expect(isPasswordValid('NoSymbols123')).toBe(false); // no symbol
  });

  it('accepts a password meeting all rules', () => {
    expect(isPasswordValid('Str0ngPass!')).toBe(true);
  });

  it('rejects an empty password', () => {
    expect(isPasswordValid('')).toBe(false);
  });
});
