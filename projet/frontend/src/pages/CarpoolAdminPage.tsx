import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { mockApi, type ApiError, type TripDto } from '../mocks/api'

export default function CarpoolAdminPage() {
  const [month, setMonth] = useState(new Date().toLocaleDateString('sv').slice(0, 7))
  const [trips, setTrips] = useState<TripDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  function load() {
    setLoading(true)
    return mockApi
      .adminTrips(month)
      .then(setTrips)
      .catch((e: ApiError) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [month])

  async function cancel(trip: TripDto) {
    setError(null)
    try {
      await mockApi.cancelTrip(trip.id)
      await load()
    } catch (e) {
      setError((e as ApiError).message)
    }
  }

  const active = trips.filter((t) => t.status === 'ACTIVE')
  const seats = active.reduce((s, t) => s + t.seats, 0)
  const booked = active.reduce((s, t) => s + t.seats - t.remainingSeats, 0)

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Administration</h1>
          <p>Trajets du mois et modération.</p>
        </div>
        <label className="field">
          <span>Mois</span>
          <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} />
        </label>
      </div>
      {error && <div className="alert alert--error">{error}</div>}
      {loading ? (
        <div className="loading">Chargement…</div>
      ) : (
        <>
          <div className="grid kpis">
            <div className="card kpi"><div className="kpi__value">{trips.length}</div><div className="kpi__label">trajets</div></div>
            <div className="card kpi"><div className="kpi__value">{trips.length - active.length}</div><div className="kpi__label">annulés</div></div>
            <div className="card kpi"><div className="kpi__value">{seats === 0 ? '—' : `${Math.round((booked / seats) * 100)} %`}</div><div className="kpi__label">taux de remplissage</div></div>
          </div>
          {trips.length === 0 ? (
            <div className="empty">Aucun trajet ce mois-ci.</div>
          ) : (
            <div className="table-wrap card">
              <table className="table">
                <thead><tr><th>Départ</th><th>Trajet</th><th>Conducteur</th><th>Places</th><th>État</th><th /></tr></thead>
                <tbody>
                  {trips.map((t) => (
                    <tr key={t.id}>
                      <td className="nowrap">{new Date(t.departureAt).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' })}</td>
                      <td><Link to={`/trips/${t.id}`}>{t.from} → {t.to}</Link></td>
                      <td>{t.driver.displayName}</td>
                      <td>{t.seats - t.remainingSeats} / {t.seats}</td>
                      <td>{t.status === 'CANCELLED' ? <span className="badge badge--danger">annulé</span> : <span className="badge badge--success">actif</span>}</td>
                      <td>{t.status === 'ACTIVE' && !t.past && <button className="btn btn--danger btn--small" onClick={() => cancel(t)}>Annuler</button>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  )
}
