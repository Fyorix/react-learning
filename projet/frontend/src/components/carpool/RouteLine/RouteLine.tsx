import styles from './RouteLine.module.scss'

export interface RouteLineProps {
  from: string
  to: string
  /** `large` pour l'en-tête d'une page de détail. */
  size?: 'medium' | 'large'
}

/** Trajet « départ ---- arrivée » avec ses deux points. */
export function RouteLine({ from, to, size = 'medium' }: RouteLineProps) {
  return (
    <div className={size === 'large' ? `${styles.route} ${styles.large}` : styles.route}>
      <span className={styles.point}>{from}</span>
      <span className={styles.line} aria-label="vers" role="img" />
      <span className={styles.point}>{to}</span>
    </div>
  )
}
