import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { mockApi, type ApiError, type TripDto } from '../mocks/api'

export default function MyTripsPage() {
  const [role, setRole] = useState<'driver' | 'passenger'>('driver')
  const [trips, setTrips] = useState<TripDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    mockApi
      .myTrips(role)
      .then(setTrips)
      .catch((e: ApiError) => setError(e.message))
      .finally(() => setLoading(false))
  }, [role])

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Mes trajets</h1>
          <p>Ceux que vous conduisez et ceux où vous avez une place.</p>
        </div>
        <div className="row">
          <button className={`btn btn--small${role === 'driver' ? ' btn--primary' : ''}`} onClick={() => setRole('driver')}>Conducteur</button>
          <button className={`btn btn--small${role === 'passenger' ? ' btn--primary' : ''}`} onClick={() => setRole('passenger')}>Passager</button>
        </div>
      </div>
      {error && <div className="alert alert--error">{error}</div>}
      {loading ? (
        <div className="loading">Chargement…</div>
      ) : trips.length === 0 ? (
        <div className="empty">
          {role === 'driver' ? <>Vous n’avez proposé aucun trajet. <Link to="/new">En proposer un</Link></> : 'Aucune réservation.'}
        </div>
      ) : (
        <div className="table-wrap card">
          <table className="table">
            <thead>
              <tr><th>Départ</th><th>Trajet</th><th>{role === 'driver' ? 'Réservations' : 'Conducteur'}</th><th>État</th></tr>
            </thead>
            <tbody>
              {trips.map((t) => (
                <tr key={t.id}>
                  <td className="nowrap">{new Date(t.departureAt).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' })}</td>
                  <td><Link to={`/trips/${t.id}`}>{t.from} → {t.to}</Link></td>
                  <td>{role === 'driver' ? `${t.seats - t.remainingSeats} / ${t.seats}` : t.driver.displayName}</td>
                  <td>
                    {t.status === 'CANCELLED' ? <span className="badge badge--danger">annulé</span>
                      : t.past ? <span className="badge">passé</span>
                        : <span className="badge badge--success">à venir</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
