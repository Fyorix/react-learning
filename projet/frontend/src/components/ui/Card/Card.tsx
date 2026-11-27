import type { ReactNode } from 'react'
import { Link } from 'react-router'
import styles from './Card.module.scss'

export interface CardProps {
  /** Titre de section affiché en tête de carte. */
  title?: string
  /** `compact` réduit la marge intérieure (barres de filtres). */
  padding?: 'normal' | 'compact'
  /** Rend toute la carte cliquable vers cette route. */
  to?: string
  /** Réservé aux composants de la bibliothèque qui étendent la carte. */
  className?: string
  children: ReactNode
}

/** Surface blanche bordée qui regroupe un contenu. */
export function Card({ title, padding = 'normal', to, className, children }: CardProps) {
  const classes = [styles.card, padding === 'compact' && styles.compact, to && styles.link, className]
    .filter(Boolean)
    .join(' ')

  if (to) {
    return (
      <Link to={to} className={classes}>
        {children}
      </Link>
    )
  }
  return (
    <div className={classes}>
      {title && <h2 className={styles.title}>{title}</h2>}
      {children}
    </div>
  )
}
