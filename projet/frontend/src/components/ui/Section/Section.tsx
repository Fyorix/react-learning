import type { ReactNode } from 'react'
import styles from './Section.module.scss'

/** Bloc séparé du contenu précédent par un grand espacement. */
export function Section({ children }: { children: ReactNode }) {
  return <section className={styles.section}>{children}</section>
}
