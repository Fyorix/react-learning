import type { ReactNode } from 'react'
import styles from './Text.module.scss'

export interface TextProps {
  /** Balise rendue : `p` pour un paragraphe, `div` pour un bloc, `span` en ligne. */
  as?: 'p' | 'div' | 'span'
  tone?: 'default' | 'muted'
  size?: 'medium' | 'small'
  weight?: 'normal' | 'strong'
  children: ReactNode
}

/** Texte courant avec les variantes typographiques de l'application. */
export function Text({ as: Tag = 'span', tone = 'default', size = 'medium', weight = 'normal', children }: TextProps) {
  const className = [tone === 'muted' && styles.muted, size === 'small' && styles.small, weight === 'strong' && styles.strong]
    .filter(Boolean)
    .join(' ')
  return <Tag className={className || undefined}>{children}</Tag>
}
