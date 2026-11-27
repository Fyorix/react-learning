// Données d'exemple pour les stories des composants métier.
import type { DemoAccount, TripDto } from '../../api/types'

const inDays = (days: number, hour: number) => {
  const d = new Date()
  d.setDate(d.getDate() + days)
  d.setHours(hour, 0, 0, 0)
  return d.toISOString()
}

export const tripFixture: TripDto = {
  id: 't-1',
  driver: { id: 'u-alice', displayName: 'Alice Martin' },
  from: 'Paris 13e',
  to: 'Kremlin-Bicêtre (campus)',
  departureAt: inDays(1, 8),
  seats: 3,
  remainingSeats: 2,
  comment: 'Départ métro Place d’Italie',
  status: 'ACTIVE',
  past: false,
  booked: false,
  isDriver: false,
  passengers: null,
}

export const demoAccountsFixture: DemoAccount[] = [
  { email: 'admin@example.com', password: 'admin123', displayName: 'Admin Démo', role: 'ADMIN' },
  { email: 'alice@example.com', password: 'alice123', displayName: 'Alice Martin', role: 'USER', note: 'conductrice' },
  { email: 'bob@example.com', password: 'bob123', displayName: 'Bob Durand', role: 'USER', note: 'passager' },
]
