import type { ReactNode } from 'react'
import { Card } from '../Card/Card'
import styles from './Toolbar.module.scss'

/** Barre de filtres : champs et boutons alignés en bas, dans une carte compacte. */
export function Toolbar({ children }: { children: ReactNode }) {
  return (
    <Card padding="compact" className={styles.toolbar}>
      {children}
    </Card>
  )
}
