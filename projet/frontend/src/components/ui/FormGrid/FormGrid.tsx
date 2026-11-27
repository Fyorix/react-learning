import type { ReactNode } from 'react'
import styles from './FormGrid.module.scss'

/** Grille de formulaire sur deux colonnes, une seule sur mobile. Un champ `fullWidth` occupe toute la ligne. */
export function FormGrid({ children }: { children: ReactNode }) {
  return <div className={styles.grid}>{children}</div>
}
