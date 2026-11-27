import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { mockApi, type ApiError, type TripDto } from '../mocks/api'

export default function TripDetailPage() {
  const { id = '' } = useParams()
  const [trip, setTrip] = useState<TripDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)

  useEffect(() => {
    mockApi
      .getTrip(id)
      .then(setTrip)
      .catch((e: ApiError) => setError(e.message))
      .finally(() => setLoading(false))
  }, [id])

  async function run(action: () => Promise<TripDto>, message: string) {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      setTrip(await action())
      setInfo(message)
    } catch (e) {
      setError((e as ApiError).message)
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <div className="loading">Chargement…</div>
  if (!trip) return <div className="alert alert--error">{error}</div>

  const departure = new Date(trip.departureAt)
  const bookable = trip.status === 'ACTIVE' && !trip.past

  return (
    <div className="narrow-page">
      <Link to="/" className="back-link">← Recherche</Link>
      {error && <div className="alert alert--error">{error}</div>}
      {info && <div className="alert alert--success">{info}</div>}
      {trip.status === 'CANCELLED' && <div className="alert alert--warning">Ce trajet a été annulé par son conducteur.</div>}

      <div className="card stack">
        <div className="route route--large">
          <span className="route__point">{trip.from}</span>
          <span className="route__line" />
          <span className="route__point">{trip.to}</span>
        </div>
        <dl className="details">
          <dt>Départ</dt>
          <dd className="strong">
            {departure.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })} à{' '}
            {departure.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
          </dd>
          <dt>Conducteur</dt>
          <dd>{trip.driver.displayName}</dd>
          <dt>Places</dt>
          <dd>{trip.remainingSeats} restante(s) sur {trip.seats}</dd>
          {trip.comment && (
            <>
              <dt>Commentaire</dt>
              <dd>{trip.comment}</dd>
            </>
          )}
        </dl>

        <div className="seats">
          {Array.from({ length: trip.seats }, (_, i) => (
            <span key={i} className={`seats__seat${i < trip.seats - trip.remainingSeats ? ' seats__seat--taken' : ''}`} />
          ))}
        </div>

        {!trip.isDriver && bookable && (
          <div className="row row--end">
            {trip.booked ? (
              <button className="btn btn--danger" disabled={busy} onClick={() => run(() => mockApi.cancelBooking(trip.id), 'Réservation annulée.')}>
                Annuler ma réservation
              </button>
            ) : (
              <button className="btn btn--primary" disabled={busy || trip.remainingSeats === 0} onClick={() => run(() => mockApi.book(trip.id), 'Place réservée. Bon voyage !')}>
                {trip.remainingSeats === 0 ? 'Complet' : 'Réserver une place'}
              </button>
            )}
          </div>
        )}
      </div>

      {trip.passengers && (
        <div className="card section">
          <h2>Passagers</h2>
          {trip.passengers.length === 0 ? (
            <p className="muted">Aucune réservation pour l’instant.</p>
          ) : (
            <ul>
              {trip.passengers.map((p) => <li key={p.id}>{p.displayName}</li>)}
            </ul>
          )}
          {trip.isDriver && bookable && (
            <div className="row section">
              <Link to={`/trips/${trip.id}/edit`} className="btn">Modifier</Link>
              <button className="btn btn--danger" disabled={busy} onClick={() => run(() => mockApi.cancelTrip(trip.id), 'Trajet annulé.')}>
                Annuler le trajet
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
