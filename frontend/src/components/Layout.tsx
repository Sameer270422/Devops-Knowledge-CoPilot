import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

function navLinkClass({ isActive }: { isActive: boolean }) {
  return `nav-link${isActive ? ' active' : ''}`;
}

export function Layout() {
  const { email, logout } = useAuth();
  return (
    <div className="app-shell">
      <header className="top-nav">
        <div className="top-nav__brand">
          <span>DevOps Knowledge Copilot</span>
        </div>
        <nav className="top-nav__links">
          <NavLink to="/documents" className={navLinkClass}>Documents</NavLink>
          <NavLink to="/chat" className={navLinkClass}>Chat</NavLink>
        </nav>
        <div className="top-nav__user">
          <span>{email}</span>
          <button className="btn btn-ghost" onClick={() => logout()}>Log out</button>
        </div>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </div>
  );
}
