import type { ReactNode } from 'react'
import styles from './NarrowContainer.module.scss'

/** Limite la largeur du contenu pour les pages de lecture et les formulaires. */
export function NarrowContainer({ children }: { children: ReactNode }) {
  return <div className={styles.container}>{children}</div>
}
