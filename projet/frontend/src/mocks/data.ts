// Données de démonstration, chargées en mémoire au démarrage de l'application.
import type { Role, TripStatus } from './api'

export interface MockUser {
  id: string
  email: string
  password: string
  displayName: string
  role: Role
  enabled: boolean
  note?: string
}

export interface TripRecord {
  id: string
  driverId: string
  from: string
  to: string
  departureAt: string
  seats: number
  comment: string | null
  status: TripStatus
  createdAt: string
}

export interface BookingRecord {
  tripId: string
  passengerId: string
  createdAt: string
}

export const users: MockUser[] = [
  { id: 'u-admin', email: 'admin@example.com', password: 'admin123', displayName: 'Admin Démo', role: 'ADMIN', enabled: true },
  { id: 'u-alice', email: 'alice@example.com', password: 'alice123', displayName: 'Alice Martin', role: 'USER', enabled: true, note: 'conductrice' },
  { id: 'u-bob', email: 'bob@example.com', password: 'bob123', displayName: 'Bob Durand', role: 'USER', enabled: true, note: 'passager' },
  { id: 'u-chloe', email: 'chloe@example.com', password: 'chloe123', displayName: 'Chloé Petit', role: 'USER', enabled: true },
]

const at = (days: number, hour: number) => {
  const d = new Date()
  d.setDate(d.getDate() + days)
  d.setHours(hour, 0, 0, 0)
  return d.toISOString()
}

export const trips: TripRecord[] = [
  { id: 't-1', driverId: 'u-alice', from: 'Paris 13e', to: 'Kremlin-Bicêtre (campus)', departureAt: at(1, 8), seats: 3, comment: 'Départ métro Place d’Italie', status: 'ACTIVE', createdAt: at(-1, 10) },
  { id: 't-2', driverId: 'u-alice', from: 'Kremlin-Bicêtre (campus)', to: 'Paris 13e', departureAt: at(1, 18), seats: 3, comment: null, status: 'ACTIVE', createdAt: at(-1, 10) },
  { id: 't-3', driverId: 'u-chloe', from: 'Ivry-sur-Seine', to: 'Villejuif', departureAt: at(2, 9), seats: 1, comment: 'Petite voiture, pas de gros bagage', status: 'ACTIVE', createdAt: at(-2, 9) },
  { id: 't-4', driverId: 'u-bob', from: 'Lyon', to: 'Paris', departureAt: at(5, 7), seats: 4, comment: 'Week-end, partage des péages', status: 'ACTIVE', createdAt: at(-3, 9) },
  { id: 't-5', driverId: 'u-chloe', from: 'Paris 5e', to: 'Kremlin-Bicêtre (campus)', departureAt: at(3, 8), seats: 2, comment: null, status: 'CANCELLED', createdAt: at(-3, 9) },
  { id: 't-6', driverId: 'u-alice', from: 'Paris 13e', to: 'Kremlin-Bicêtre (campus)', departureAt: at(-2, 8), seats: 3, comment: null, status: 'ACTIVE', createdAt: at(-6, 9) },
]

export const bookings: BookingRecord[] = [
  { tripId: 't-1', passengerId: 'u-bob', createdAt: at(-1, 12) },
  { tripId: 't-3', passengerId: 'u-alice', createdAt: at(-1, 12) },
  { tripId: 't-6', passengerId: 'u-bob', createdAt: at(-4, 12) },
]
