import type { TripDto } from '../../../api/types'
import { Badge } from '../../ui'

export interface TripStatusBadgeProps {
  trip: Pick<TripDto, 'status' | 'past'>
  /** `timeline` distingue « passé » et « à venir » ; `moderation` affiche seulement « actif » ou « annulé ». */
  view?: 'timeline' | 'moderation'
}

/** État d'un trajet : annulé, passé, à venir ou actif. */
export function TripStatusBadge({ trip, view = 'timeline' }: TripStatusBadgeProps) {
  if (trip.status === 'CANCELLED') return <Badge tone="danger">annulé</Badge>
  if (view === 'moderation') return <Badge tone="success">actif</Badge>
  if (trip.past) return <Badge>passé</Badge>
  return <Badge tone="success">à venir</Badge>
}
