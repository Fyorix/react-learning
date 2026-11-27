import type { ReactNode } from 'react'
import { Card } from '../Card/Card'
import styles from './Kpi.module.scss'

export interface KpiProps {
  value: ReactNode
  label: string
}

/** Indicateur chiffré : une valeur mise en avant et son libellé. */
export function Kpi({ value, label }: KpiProps) {
  return (
    <Card className={styles.kpi}>
      <div className={styles.value}>{value}</div>
      <div className={styles.label}>{label}</div>
    </Card>
  )
}

/** Grille responsive d'indicateurs. */
export function KpiGrid({ children }: { children: ReactNode }) {
  return <div className={styles.grid}>{children}</div>
}
