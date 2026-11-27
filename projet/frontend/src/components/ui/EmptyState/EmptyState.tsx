import type { ReactNode } from 'react'
import styles from './EmptyState.module.scss'

export interface EmptyStateProps {
  children: ReactNode
}

/** Message affiché à la place d'une liste vide ; peut contenir un lien d'action. */
export function EmptyState({ children }: EmptyStateProps) {
  return <div className={styles.empty}>{children}</div>
}
