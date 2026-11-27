import { Link } from 'react-router'
import styles from './BackLink.module.scss'

export interface BackLinkProps {
  to: string
  label: string
}

/** Lien de retour discret, placé en haut d'une page de détail. */
export function BackLink({ to, label }: BackLinkProps) {
  return (
    <Link to={to} className={styles.link}>
      ← {label}
    </Link>
  )
}
