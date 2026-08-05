// The /vitest subpath (rather than the plain package import) is what actually augments
// Vitest's `expect` type with jest-dom's matchers (toBeInTheDocument, toHaveClass, etc.) —
// the plain import only types Jest's `expect`, which would make `npm run build`'s
// `tsc --noEmit` step fail on every matcher call in the test files even though the tests
// themselves run fine under vitest at runtime.
import '@testing-library/jest-dom/vitest';
