import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="auth-shell">
      <div className="card auth-card" style={{ textAlign: 'center' }}>
        <h1>Page not found</h1>
        <p className="text-muted">The page you're looking for doesn't exist.</p>
        <Link to="/" className="btn btn-primary">
          Go home
        </Link>
      </div>
    </div>
  )
}
