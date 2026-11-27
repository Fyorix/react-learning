import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import { mockApi, type ApiError } from '../mocks/api'

function toLocalInput(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export default function TripFormPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [departure, setDeparture] = useState('')
  const [seats, setSeats] = useState(3)
  const [comment, setComment] = useState('')
  const [loading, setLoading] = useState(Boolean(id))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  useEffect(() => {
    if (!id) return
    mockApi
      .getTrip(id)
      .then((t) => {
        setFrom(t.from)
        setTo(t.to)
        setDeparture(toLocalInput(t.departureAt))
        setSeats(t.seats)
        setComment(t.comment ?? '')
      })
      .catch((e: ApiError) => setError(e))
      .finally(() => setLoading(false))
  }, [id])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const req = { from, to, departureAt: departure ? new Date(departure).toISOString() : '', seats, comment }
      const trip = id ? await mockApi.updateTrip(id, req) : await mockApi.createTrip(req)
      navigate(`/trips/${trip.id}`)
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="loading">Chargement…</div>

  return (
    <div className="narrow-page">
      <div className="page-header">
        <h1>{id ? 'Modifier le trajet' : 'Proposer un trajet'}</h1>
      </div>
      {error && <div className="alert alert--error">{error.message}</div>}
      <form className="card stack" onSubmit={onSubmit}>
        <div className="form-grid">
          <label className="field">
            <span>Départ</span>
            <input value={from} onChange={(e) => setFrom(e.target.value)} required />
            {error?.fields?.from && <span className="field__error">{error.fields.from}</span>}
          </label>
          <label className="field">
            <span>Arrivée</span>
            <input value={to} onChange={(e) => setTo(e.target.value)} required />
            {error?.fields?.to && <span className="field__error">{error.fields.to}</span>}
          </label>
          <label className="field">
            <span>Date et heure de départ</span>
            <input type="datetime-local" value={departure} onChange={(e) => setDeparture(e.target.value)} required />
            {error?.fields?.departureAt && <span className="field__error">{error.fields.departureAt}</span>}
          </label>
          <label className="field">
            <span>Places proposées (1 à 8)</span>
            <input type="number" min={1} max={8} value={seats} onChange={(e) => setSeats(Number(e.target.value))} required />
            {error?.fields?.seats && <span className="field__error">{error.fields.seats}</span>}
          </label>
          <label className="field full">
            <span>Commentaire (facultatif)</span>
            <textarea value={comment} maxLength={500} onChange={(e) => setComment(e.target.value)} placeholder="Point de rendez-vous, bagages, musique…" />
          </label>
        </div>
        <div className="row row--end">
          <button type="button" className="btn btn--ghost" onClick={() => navigate(-1)}>Annuler</button>
          <button className="btn btn--primary" type="submit" disabled={saving}>{saving ? 'Enregistrement…' : 'Publier'}</button>
        </div>
      </form>
    </div>
  )
}
