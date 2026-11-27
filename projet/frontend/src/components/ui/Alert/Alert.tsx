import type { ReactNode } from 'react'
import styles from './Alert.module.scss'

export type AlertTone = 'error' | 'success' | 'info' | 'warning'

export interface AlertProps {
  tone: AlertTone
  children: ReactNode
}

/** Message de retour. Les erreurs et avertissements sont annoncés immédiatement aux lecteurs d'écran. */
export function Alert({ tone, children }: AlertProps) {
  const urgent = tone === 'error' || tone === 'warning'
  return (
    <div className={`${styles.alert} ${styles[tone]}`} role={urgent ? 'alert' : 'status'}>
      {children}
    </div>
  )
}
