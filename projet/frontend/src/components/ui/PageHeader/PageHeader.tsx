import type { ReactNode } from 'react'
import styles from './PageHeader.module.scss'

export interface PageHeaderProps {
  title: string
  description?: string
  /** Actions ou filtres alignés à droite du titre. */
  actions?: ReactNode
}

/** En-tête de page : titre principal, sous-titre et actions. */
export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className={styles.header}>
      <div>
        <h1>{title}</h1>
        {description && <p className={styles.description}>{description}</p>}
      </div>
      {actions}
    </div>
  )
}
