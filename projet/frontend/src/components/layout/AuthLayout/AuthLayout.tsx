import type { ReactNode } from 'react'
import { Card } from '../../ui'
import styles from './AuthLayout.module.scss'

export interface AuthLayoutProps {
  title: string
  subtitle?: string
  children: ReactNode
}

/** Écran plein centré pour les pages publiques (connexion). */
export function AuthLayout({ title, subtitle, children }: AuthLayoutProps) {
  return (
    <div className={styles.screen}>
      <Card className={styles.card}>
        <h1 className={styles.title}>{title}</h1>
        {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
        {children}
      </Card>
    </div>
  )
}
