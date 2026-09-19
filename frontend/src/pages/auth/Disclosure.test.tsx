import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AboutPage } from '../AboutPage'
import { CustomerLoginPage } from './CustomerLoginPage'
import { RegisterPage } from './RegisterPage'

function wrap(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('fictional-funds disclosure', () => {
  it('is a discreet one-line note on the login page linking to About, not a banner', () => {
    const { container } = wrap(<CustomerLoginPage />)

    const note = screen.getByText(/uses fictional funds only/i)
    expect(note).toHaveClass('fine-print')
    expect(screen.getByRole('link', { name: 'About' })).toHaveAttribute('href', '/about')
    expect(container.querySelector('.demo-banner')).toBeNull()
    expect(screen.queryByText(/Demo banking/i)).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument()
  })

  it('is also present on registration', () => {
    wrap(<RegisterPage />)
    expect(screen.getByText(/uses fictional funds only/i)).toBeInTheDocument()
  })

  it('the About page states that funds are fictional and makes no compliance claim', () => {
    wrap(<AboutPage />)
    expect(screen.getByText(/All money in it is fictional/)).toBeInTheDocument()
    expect(screen.getByText(/not a licensed bank/i)).toBeInTheDocument()
    expect(screen.getByText(/any claim of regulatory approval or compliance/i)).toBeInTheDocument()
  })
})
