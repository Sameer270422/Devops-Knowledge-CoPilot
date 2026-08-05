import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import { isValidEmail, isPasswordValid, passwordRules } from '../utils/validation';

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [touchedEmail, setTouchedEmail] = useState(false);
  const [passwordFocused, setPasswordFocused] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const emailInvalid = touchedEmail && email.length > 0 && !isValidEmail(email);
  const showChecklist = passwordFocused || password.length > 0;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setTouchedEmail(true);
    setError(null);

    if (!isValidEmail(email)) {
      setError('Enter a valid email address.');
      return;
    }
    if (!isPasswordValid(password)) {
      setError('Password does not meet the requirements below.');
      return;
    }

    setSubmitting(true);
    try {
      await register(email, password);
      navigate('/documents');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Registration failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <h1>Create your account</h1>
          <p>Bring your own documents, ask questions, get grounded answers</p>
        </div>
        <div className="card card-pad">
          {error && <div className="banner banner-error">{error}</div>}
          <form onSubmit={onSubmit} noValidate>
            <div className="field">
              <label htmlFor="email">Email</label>
              <input
                id="email"
                type="email"
                className={`input${emailInvalid ? ' input-error' : ''}`}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                onBlur={() => setTouchedEmail(true)}
                autoComplete="email"
                required
              />
              {emailInvalid && <div className="field-error">Enter a valid email address.</div>}
            </div>
            <div className="field">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                className="input"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onFocus={() => setPasswordFocused(true)}
                autoComplete="new-password"
                required
              />
              {showChecklist && (
                <ul className="checklist">
                  {passwordRules.map((rule) => {
                    const met = rule.test(password);
                    return (
                      <li key={rule.key} className={met ? 'met' : ''}>
                        <span className="dot">{met ? '✓' : ''}</span>
                        {rule.label}
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
            <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
              {submitting ? 'Creating…' : 'Create account'}
            </button>
          </form>
        </div>
        <p className="auth-footer">Already have an account? <Link to="/login">Sign in</Link></p>
      </div>
    </div>
  );
}
