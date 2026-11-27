import type { ReactNode } from 'react'
import styles from './Badge.module.scss'

export type BadgeTone = 'neutral' | 'accent' | 'success' | 'warning' | 'danger' | 'info'

export interface BadgeProps {
  tone?: BadgeTone
  children: ReactNode
}

/** Pastille de statut. Placée après un texte, elle prend une marge à gauche. */
export function Badge({ tone = 'neutral', children }: BadgeProps) {
  const className = tone === 'neutral' ? styles.badge : `${styles.badge} ${styles[tone]}`
  return <span className={className}>{children}</span>
}
