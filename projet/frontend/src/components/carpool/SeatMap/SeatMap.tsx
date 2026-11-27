import styles from './SeatMap.module.scss'

export interface SeatMapProps {
  seats: number
  remainingSeats: number
}

/** Représentation visuelle des places : pleines si réservées, vides si libres. */
export function SeatMap({ seats, remainingSeats }: SeatMapProps) {
  const taken = seats - remainingSeats
  return (
    <div className={styles.seats} role="img" aria-label={`${taken} place(s) réservée(s) sur ${seats}`}>
      {Array.from({ length: seats }, (_, i) => (
        <span key={i} className={i < taken ? `${styles.seat} ${styles.taken}` : styles.seat} />
      ))}
    </div>
  )
}
