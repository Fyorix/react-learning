import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { mockApi, type ApiError, type Page, type TripDto } from '../mocks/api'

export default function SearchPage() {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [date, setDate] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<TripDto> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    mockApi
      .searchTrips(from, to, date, page, 10)
      .then(setData)
      .catch((e: ApiError) => setError(e.message))
      .finally(() => setLoading(false))
  }, [from, to, date, page])

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Trouver un trajet</h1>
          <p>Partagez la route avec vos collègues.</p>
        </div>
        <Link to="/new" className="btn btn--primary">Proposer un trajet</Link>
      </div>

      <div className="card toolbar search-bar">
        <label className="field">
          <span>Départ</span>
          <input value={from} placeholder="ex. Paris" onChange={(e) => { setFrom(e.target.value); setPage(0) }} />
        </label>
        <label className="field">
          <span>Arrivée</span>
          <input value={to} placeholder="ex. campus" onChange={(e) => { setTo(e.target.value); setPage(0) }} />
        </label>
        <label className="field">
          <span>Date</span>
          <input type="date" value={date} onChange={(e) => { setDate(e.target.value); setPage(0) }} />
        </label>
        {(from || to || date) && (
          <button className="btn btn--ghost" onClick={() => { setFrom(''); setTo(''); setDate('') }}>Effacer</button>
        )}
      </div>

      {error && <div className="alert alert--error">{error}</div>}
      {loading || !data ? (
        <div className="loading">Chargement…</div>
      ) : data.content.length === 0 ? (
        <div className="empty">Aucun trajet à venir ne correspond. Et si vous en proposiez un ?</div>
      ) : (
        <>
          <div className="stack">
            {data.content.map((t) => (
              <Link key={t.id} to={`/trips/${t.id}`} className="card card--link trip-card">
                <div className="trip-card__time">
                  <div className="trip-card__day">{new Date(t.departureAt).toLocaleDateString('fr-FR', { weekday: 'short', day: 'numeric', month: 'short' })}</div>
                  <div className="trip-card__hour">{new Date(t.departureAt).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}</div>
                </div>
                <div className="trip-card__route">
                  <div className="route">
                    <span className="route__point">{t.from}</span>
                    <span className="route__line" />
                    <span className="route__point">{t.to}</span>
                  </div>
                  <div className="card__meta">avec {t.driver.displayName}</div>
                </div>
                <div className="trip-card__seats">
                  {t.isDriver ? (
                    <span className="badge badge--accent">votre trajet</span>
                  ) : t.booked ? (
                    <span className="badge badge--success">réservé</span>
                  ) : t.remainingSeats === 0 ? (
                    <span className="badge badge--danger">complet</span>
                  ) : (
                    <span className="badge badge--info">{t.remainingSeats} place(s)</span>
                  )}
                </div>
              </Link>
            ))}
          </div>
          {data.totalPages > 1 && (
            <div className="pagination">
              <button className="btn btn--small" disabled={page === 0} onClick={() => setPage(page - 1)}>Précédent</button>
              <span>Page {page + 1} / {data.totalPages}</span>
              <button className="btn btn--small" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Suivant</button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
