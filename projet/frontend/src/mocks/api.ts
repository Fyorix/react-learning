// Appels bouchonnés : chaque fonction reproduit un endpoint du backend (même route, mêmes DTO,
// mêmes règles métier, même format d'erreur). Aucun appel réseau : tout est en mémoire.
import { users, type MockUser } from './data'

// ---------- types communs ----------

export type Role = 'ADMIN' | 'USER'

export interface UserDto {
  id: string
  email: string
  displayName: string
  role: Role
  enabled: boolean
}

export interface UserSummary {
  id: string
  displayName: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserDto
}

/** Format d'erreur unique de l'API (§4.5). */
export interface ApiError {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  fields?: Record<string, string>
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface DemoAccount {
  email: string
  password: string
  displayName: string
  role: Role
  note?: string
}

// ---------- outillage du bouchon ----------

class Failure {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly message: string,
    readonly fields?: Record<string, string>,
  ) {}
}

function fail(status: number, code: string, message: string, fields?: Record<string, string>): never {
  throw new Failure(status, code, message, fields)
}

const notFound = (what: string): never => fail(404, 'NOT_FOUND', `${what} introuvable`)
const forbidden = (message: string): never => fail(403, 'FORBIDDEN', message)
const conflict = (code: string, message: string): never => fail(409, code, message)
const invalid = (field: string, message: string): never =>
  fail(400, 'VALIDATION_ERROR', 'Données invalides', { [field]: message })

/** Délai simulé (200 à 500 ms), copie profonde du résultat, conversion des erreurs au format de l'API. */
async function call<T>(path: string, fn: () => T): Promise<T> {
  await new Promise((resolve) => setTimeout(resolve, 200 + Math.floor(Math.random() * 300)))
  try {
    const result = fn()
    return result === undefined ? result : (JSON.parse(JSON.stringify(result)) as T)
  } catch (e) {
    if (e instanceof Failure) {
      const error: ApiError = {
        timestamp: new Date().toISOString(),
        status: e.status,
        code: e.code,
        message: e.message,
        path,
        ...(e.fields ? { fields: e.fields } : {}),
      }
      throw error
    }
    throw e
  }
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

function paginate<T>(all: T[], page = 0, size = 20): Page<T> {
  const safeSize = Math.max(1, Math.min(size, 100))
  const safePage = Math.max(0, page)
  return {
    content: all.slice(safePage * safeSize, safePage * safeSize + safeSize),
    page: safePage,
    size: safeSize,
    totalElements: all.length,
    totalPages: Math.ceil(all.length / safeSize),
  }
}

const nowIso = () => new Date().toISOString()
const todayIso = () => toIsoDate(new Date())

function toIsoDate(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function addDays(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T12:00:00`)
  d.setDate(d.getDate() + days)
  return toIsoDate(d)
}

// ---------- session et utilisateurs ----------

let session: MockUser | null = null

function currentUser(): MockUser {
  if (!session) fail(401, 'UNAUTHORIZED', 'Authentification requise')
  return session as MockUser
}

function requireAdmin(): MockUser {
  const me = currentUser()
  if (me.role !== 'ADMIN') forbidden('Action réservée aux administrateurs')
  return me
}

const isAdmin = (u: MockUser) => u.role === 'ADMIN'

function nameOf(id: string): string {
  return users.find((u) => u.id === id)?.displayName ?? 'Utilisateur supprimé'
}

function summary(id: string): UserSummary {
  return { id, displayName: nameOf(id) }
}

function toUserDto(u: MockUser): UserDto {
  return { id: u.id, email: u.email, displayName: u.displayName, role: u.role, enabled: u.enabled }
}

/** POST /api/auth/login */
function login(email: string, password: string): Promise<TokenResponse> {
  return call('/api/auth/login', () => {
    const user = users.find((u) => u.email === email.trim().toLowerCase() && u.password === password)
    if (!user) fail(401, 'BAD_CREDENTIALS', 'Email ou mot de passe incorrect')
    const u = user as MockUser
    if (!u.enabled) fail(403, 'ACCOUNT_DISABLED', 'Ce compte est désactivé')
    session = u
    return { accessToken: `mock-access-${u.id}`, refreshToken: `mock-refresh-${u.id}`, expiresIn: 900, user: toUserDto(u) }
  })
}

/** GET /api/auth/me */
function me(): Promise<UserDto> {
  return call('/api/auth/me', () => toUserDto(currentUser()))
}

function logout(): void {
  session = null
}

/** Uniquement pour la page Connexion : rappel des identifiants de démonstration. */
function demoAccounts(): DemoAccount[] {
  return users
    .filter((u) => u.enabled)
    .map((u) => ({ email: u.email, password: u.password, displayName: u.displayName, role: u.role, note: u.note }))
}

const authApi = { login, me, logout, demoAccounts }

// Les déclarations ci-dessous peuvent ne pas toutes servir dans chaque application.
void notFound; void forbidden; void conflict; void invalid; void paginate; void nowIso; void todayIso
void addDays; void requireAdmin; void isAdmin; void nameOf; void summary; void uuid

// ======================================================================
// API de l'application
// ======================================================================
import { bookings, trips, type TripRecord } from './data'

export type TripStatus = 'ACTIVE' | 'CANCELLED'

export interface TripRequest {
  from: string
  to: string
  departureAt: string
  seats: number
  comment?: string | null
}

export interface TripDto {
  id: string
  driver: UserSummary
  from: string
  to: string
  departureAt: string
  seats: number
  remainingSeats: number
  comment: string | null
  status: TripStatus
  past: boolean
  booked: boolean
  isDriver: boolean
  passengers: UserSummary[] | null
}

const findTrip = (id: string) => trips.find((t) => t.id === id) ?? notFound('Trajet')

function toDto(t: TripRecord): TripDto {
  const me = currentUser()
  const list = bookings.filter((b) => b.tripId === t.id)
  const isDriver = t.driverId === me.id
  return {
    id: t.id,
    driver: summary(t.driverId),
    from: t.from,
    to: t.to,
    departureAt: t.departureAt,
    seats: t.seats,
    remainingSeats: Math.max(0, t.seats - list.length),
    comment: t.comment,
    status: t.status,
    past: t.departureAt <= nowIso(),
    booked: list.some((b) => b.passengerId === me.id),
    isDriver,
    passengers: isDriver || isAdmin(me) ? list.map((b) => summary(b.passengerId)) : null,
  }
}

function validate(req: TripRequest) {
  const fields: Record<string, string> = {}
  if (!req.from.trim()) fields.from = 'ne doit pas être vide'
  if (!req.to.trim()) fields.to = 'ne doit pas être vide'
  if (!Number.isInteger(req.seats) || req.seats < 1 || req.seats > 8) fields.seats = 'doit être compris entre 1 et 8'
  if (!req.departureAt || req.departureAt <= nowIso()) fields.departureAt = 'Le départ doit être dans le futur'
  if (Object.keys(fields).length) fail(400, 'VALIDATION_ERROR', 'Données invalides', fields)
}

/** POST /api/carpool/trips */
function createTrip(req: TripRequest): Promise<TripDto> {
  return call('/api/carpool/trips', () => {
    const me = currentUser()
    validate(req)
    const trip: TripRecord = {
      id: uuid(), driverId: me.id, from: req.from.trim(), to: req.to.trim(), departureAt: req.departureAt, seats: req.seats,
      comment: req.comment?.trim() || null, status: 'ACTIVE', createdAt: nowIso(),
    }
    trips.push(trip)
    return toDto(trip)
  })
}

/** GET /api/carpool/trips?from=&to=&date= — trajets à venir, actifs. */
function searchTrips(from = '', to = '', date = '', page = 0, size = 20): Promise<Page<TripDto>> {
  return call('/api/carpool/trips', () => {
    const match = (value: string, filter: string) => !filter.trim() || value.toLowerCase().includes(filter.trim().toLowerCase())
    const list = trips
      .filter((t) => t.status === 'ACTIVE' && t.departureAt > nowIso())
      .filter((t) => match(t.from, from) && match(t.to, to))
      .filter((t) => !date || t.departureAt.slice(0, 10) === date || new Date(t.departureAt).toLocaleDateString('sv') === date)
      .sort((a, b) => a.departureAt.localeCompare(b.departureAt))
      .map(toDto)
    return paginate(list, page, size)
  })
}

/** GET /api/carpool/trips/{id} */
function getTrip(id: string): Promise<TripDto> {
  return call(`/api/carpool/trips/${id}`, () => toDto(findTrip(id)))
}

/** PUT /api/carpool/trips/{id} — conducteur ou ADMIN ; pas moins de places que de réservations. */
function updateTrip(id: string, req: TripRequest): Promise<TripDto> {
  return call(`/api/carpool/trips/${id}`, () => {
    const me = currentUser()
    const trip = findTrip(id)
    if (!isAdmin(me) && trip.driverId !== me.id) forbidden('Action réservée au propriétaire ou à un administrateur')
    if (trip.status === 'CANCELLED') conflict('TRIP_CANCELLED', 'Le trajet est annulé')
    validate(req)
    const booked = bookings.filter((b) => b.tripId === id).length
    if (req.seats < booked) {
      conflict('SEATS_BELOW_BOOKINGS', `Il y a déjà ${booked} réservation(s) : impossible de descendre à ${req.seats} place(s)`)
    }
    Object.assign(trip, { from: req.from.trim(), to: req.to.trim(), departureAt: req.departureAt, seats: req.seats, comment: req.comment?.trim() || null })
    return toDto(trip)
  })
}

/** POST /api/carpool/trips/{id}/cancel */
function cancelTrip(id: string): Promise<TripDto> {
  return call(`/api/carpool/trips/${id}/cancel`, () => {
    const me = currentUser()
    const trip = findTrip(id)
    if (!isAdmin(me) && trip.driverId !== me.id) forbidden('Action réservée au propriétaire ou à un administrateur')
    trip.status = 'CANCELLED'
    return toDto(trip)
  })
}

/** POST /api/carpool/trips/{id}/bookings */
function book(id: string): Promise<TripDto> {
  return call(`/api/carpool/trips/${id}/bookings`, () => {
    const me = currentUser()
    const trip = findTrip(id)
    if (trip.status === 'CANCELLED' || trip.departureAt <= nowIso()) conflict('TRIP_NOT_BOOKABLE', "Ce trajet n'est plus réservable")
    if (trip.driverId === me.id) conflict('OWN_TRIP', 'Impossible de réserver son propre trajet')
    if (bookings.some((b) => b.tripId === id && b.passengerId === me.id)) conflict('ALREADY_BOOKED', 'Vous avez déjà une place sur ce trajet')
    if (bookings.filter((b) => b.tripId === id).length >= trip.seats) conflict('NO_SEAT_AVAILABLE', 'Plus aucune place disponible')
    bookings.push({ tripId: id, passengerId: me.id, createdAt: nowIso() })
    return toDto(trip)
  })
}

/** DELETE /api/carpool/trips/{id}/bookings/me */
function cancelBooking(id: string): Promise<TripDto> {
  return call(`/api/carpool/trips/${id}/bookings/me`, () => {
    const me = currentUser()
    const trip = findTrip(id)
    const index = bookings.findIndex((b) => b.tripId === id && b.passengerId === me.id)
    if (index < 0) notFound('Réservation')
    if (trip.departureAt <= nowIso()) conflict('TRIP_NOT_BOOKABLE', 'Le trajet est passé')
    bookings.splice(index, 1)
    return toDto(trip)
  })
}

/** GET /api/carpool/me/trips?role=driver|passenger */
function myTrips(role: 'driver' | 'passenger'): Promise<TripDto[]> {
  return call('/api/carpool/me/trips', () => {
    const me = currentUser()
    const ids = new Set(bookings.filter((b) => b.passengerId === me.id).map((b) => b.tripId))
    return trips
      .filter((t) => (role === 'driver' ? t.driverId === me.id : ids.has(t.id)))
      .sort((a, b) => b.departureAt.localeCompare(a.departureAt))
      .map(toDto)
  })
}

/** GET /api/carpool/admin/trips?month=YYYY-MM — ADMIN */
function adminTrips(month: string): Promise<TripDto[]> {
  return call('/api/carpool/admin/trips', () => {
    requireAdmin()
    return trips
      .filter((t) => new Date(t.departureAt).toLocaleDateString('sv').slice(0, 7) === month)
      .sort((a, b) => a.departureAt.localeCompare(b.departureAt))
      .map(toDto)
  })
}

export const mockApi = { ...authApi, createTrip, searchTrips, getTrip, updateTrip, cancelTrip, book, cancelBooking, myTrips, adminTrips }
