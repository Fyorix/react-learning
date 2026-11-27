import type { ReactNode } from 'react'
import styles from './Row.module.scss'

export interface RowProps {
  /** Alignement horizontal : `end` pour une barre d'actions de formulaire. */
  justify?: 'start' | 'between' | 'end'
  children: ReactNode
}

/** Aligne ses enfants horizontalement, avec retour à la ligne si la place manque. */
export function Row({ justify = 'start', children }: RowProps) {
  return <div className={justify === 'start' ? styles.row : `${styles.row} ${styles[justify]}`}>{children}</div>
}
