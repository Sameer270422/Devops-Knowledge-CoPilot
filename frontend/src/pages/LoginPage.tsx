import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import { isValidEmail } from '../utils/validation';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [touchedEmail, setTouchedEmail] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const emailInvalid = touchedEmail && email.length > 0 && !isValidEmail(email);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setTouchedEmail(true);
    setError(null);
    if (!isValidEmail(email)) {
      setError('Enter a valid email address.');
      return;
    }
    setSubmitting(true);
    try {
      await login(email, password);
      navigate('/documents');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Login failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <h1>DevOps Knowledge Copilot</h1>
          <p>Sign in to your workspace</p>
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
                autoComplete="current-password"
                required
              />
            </div>
            <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
              {submitting ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        </div>
        <p className="auth-footer">No account? <Link to="/register">Register</Link></p>
      </div>
    </div>
  );
}
