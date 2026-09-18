import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StatusBadge } from './StatusBadge'

describe('StatusBadge', () => {
  it('renders the status text', () => {
    render(<StatusBadge status="ACTIVE" />)
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
  })

  it('renders underscored statuses with a space', () => {
    render(<StatusBadge status="IN_PROGRESS" />)
    expect(screen.getByText('IN PROGRESS')).toBeInTheDocument()
  })

  it('falls back to a neutral style for an unrecognized status instead of crashing', () => {
    render(<StatusBadge status="SOMETHING_UNEXPECTED" />)
    expect(screen.getByText('SOMETHING UNEXPECTED')).toBeInTheDocument()
  })
})
