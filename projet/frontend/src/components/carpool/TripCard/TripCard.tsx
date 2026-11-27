import type { TripDto } from '../../../api/types'
import { formatShortDay, formatTime } from '../../../utils/date'
import { Badge, Card } from '../../ui'
import { RouteLine } from '../RouteLine/RouteLine'
import styles from './TripCard.module.scss'

export interface TripCardProps {
  trip: TripDto
}

function Availability({ trip }: TripCardProps) {
  if (trip.isDriver) return <Badge tone="accent">votre trajet</Badge>
  if (trip.booked) return <Badge tone="success">réservé</Badge>
  if (trip.remainingSeats === 0) return <Badge tone="danger">complet</Badge>
  return <Badge tone="info">{trip.remainingSeats} place(s)</Badge>
}

/** Résultat de recherche cliquable : horaire, trajet, conducteur et disponibilité. */
export function TripCard({ trip }: TripCardProps) {
  return (
    <Card to={`/trips/${trip.id}`} className={styles.layout}>
      <div>
        <div className={styles.day}>{formatShortDay(trip.departureAt)}</div>
        <div className={styles.hour}>{formatTime(trip.departureAt)}</div>
      </div>
      <div>
        <RouteLine from={trip.from} to={trip.to} />
        <div className={styles.driver}>avec {trip.driver.displayName}</div>
      </div>
      <div>
        <Availability trip={trip} />
      </div>
    </Card>
  )
}
