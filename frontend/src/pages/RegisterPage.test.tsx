import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RegisterPage } from './RegisterPage';
import { ApiError } from '../api/client';

const navigateMock = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigateMock };
});

const registerMock = vi.fn();
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ register: registerMock }),
}));

function renderPage() {
  return render(
    <MemoryRouter>
      <RegisterPage />
    </MemoryRouter>,
  );
}

describe('RegisterPage', () => {
  beforeEach(() => {
    navigateMock.mockReset();
    registerMock.mockReset();
  });

  it('blocks submission and shows an error for an invalid email', async () => {
    renderPage();
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'not-an-email' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Str0ngPass!' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    // The same message legitimately appears twice: once in the top-level error banner,
    // once as the inline field-level error under the email input.
    const matches = await screen.findAllByText('Enter a valid email address.');
    expect(matches).toHaveLength(2);
    expect(registerMock).not.toHaveBeenCalled();
  });

  it('blocks submission and shows an error for a password that fails the complexity rules', async () => {
    renderPage();
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'weak' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText('Password does not meet the requirements below.')).toBeInTheDocument();
    expect(registerMock).not.toHaveBeenCalled();
  });

  it('shows the live password checklist and marks rules as met while typing', () => {
    renderPage();
    const passwordInput = screen.getByLabelText('Password');
    fireEvent.focus(passwordInput);

    // Unmet yet: the rule's leading "dot" span is still empty, so the <li>'s full text
    // content is exactly the label.
    expect(screen.getByText(/At least 10 characters/)).toBeInTheDocument();

    fireEvent.change(passwordInput, { target: { value: 'Str0ngPass!' } });
    // Once met, the dot span renders a checkmark right before the label with no
    // separating space (JSX collapses the whitespace-only line between them), so the
    // <li>'s exact text becomes "✓At least 10 characters" — a regex match sidesteps
    // that formatting detail instead of asserting on it.
    const lengthRule = screen.getByText(/At least 10 characters/).closest('li');
    expect(lengthRule).toHaveClass('met');
  });

  it('calls register and navigates to /documents on success', async () => {
    registerMock.mockResolvedValueOnce(undefined);
    renderPage();
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Str0ngPass!' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => expect(registerMock).toHaveBeenCalledWith('user@example.com', 'Str0ngPass!'));
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/documents'));
  });

  it('shows the server error message when registration fails (e.g. email already in use)', async () => {
    registerMock.mockRejectedValueOnce(new ApiError(409, 'Email already registered'));
    renderPage();
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Str0ngPass!' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText('Email already registered')).toBeInTheDocument();
    expect(navigateMock).not.toHaveBeenCalled();
  });
});
