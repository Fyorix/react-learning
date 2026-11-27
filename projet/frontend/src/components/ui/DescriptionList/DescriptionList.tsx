import type { ReactNode } from 'react'
import styles from './DescriptionList.module.scss'

export interface DescriptionItem {
  term: string
  description: ReactNode
  /** Met la valeur en gras (information principale). */
  strong?: boolean
}

export interface DescriptionListProps {
  items: DescriptionItem[]
}

/** Liste libellé / valeur sur deux colonnes. */
export function DescriptionList({ items }: DescriptionListProps) {
  return (
    <dl className={styles.list}>
      {items.map((item) => (
        <div key={item.term} className={styles.item}>
          <dt className={styles.term}>{item.term}</dt>
          <dd className={item.strong ? `${styles.description} ${styles.strong}` : styles.description}>
            {item.description}
          </dd>
        </div>
      ))}
    </dl>
  )
}
