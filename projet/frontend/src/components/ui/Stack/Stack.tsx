import type { ReactNode } from 'react'
import styles from './Stack.module.scss'

export interface StackProps {
  gap?: 'small' | 'medium'
  children: ReactNode
}

/** Empile ses enfants verticalement avec un espacement régulier. */
export function Stack({ gap = 'medium', children }: StackProps) {
  return <div className={gap === 'small' ? `${styles.stack} ${styles.small}` : styles.stack}>{children}</div>
}
