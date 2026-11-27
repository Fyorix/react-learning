import type { ReactNode } from 'react'
import styles from './Table.module.scss'

export interface TableColumn<T> {
  key: string
  /** En-tête de colonne ; chaîne vide pour une colonne d'actions. */
  header: string
  render: (row: T) => ReactNode
  /** Empêche le retour à la ligne (dates, montants). */
  nowrap?: boolean
}

export interface TableProps<T> {
  columns: TableColumn<T>[]
  rows: T[]
  rowKey: (row: T) => string
  compact?: boolean
}

/** Tableau de données décrit par ses colonnes ; défile horizontalement sur petit écran. */
export function Table<T>({ columns, rows, rowKey, compact = false }: TableProps<T>) {
  return (
    <div className={styles.wrap}>
      <table className={compact ? `${styles.table} ${styles.compact}` : styles.table}>
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key}>{c.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((c) => (
                <td key={c.key} className={c.nowrap ? styles.nowrap : undefined}>
                  {c.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
