import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Link } from 'react-router'
import styles from './Button.module.scss'

export type ButtonVariant = 'default' | 'primary' | 'danger' | 'ghost'
export type ButtonSize = 'medium' | 'small'

interface ButtonStyleProps {
  /** Apparence : `primary` pour l'action principale, `danger` pour une action destructrice. */
  variant?: ButtonVariant
  size?: ButtonSize
  children: ReactNode
}

function classNameFor(variant: ButtonVariant, size: ButtonSize): string {
  return [styles.button, variant !== 'default' && styles[variant], size === 'small' && styles.small]
    .filter(Boolean)
    .join(' ')
}

export type ButtonProps = ButtonStyleProps & Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className' | 'style'>

/** Bouton d'action. `type="button"` par défaut : passer `type="submit"` dans un formulaire. */
export function Button({ variant = 'default', size = 'medium', type = 'button', children, ...rest }: ButtonProps) {
  return (
    <button {...rest} type={type} className={classNameFor(variant, size)}>
      {children}
    </button>
  )
}

export interface ButtonLinkProps extends ButtonStyleProps {
  to: string
}

/** Lien de navigation qui a l'apparence d'un bouton. */
export function ButtonLink({ to, variant = 'default', size = 'medium', children }: ButtonLinkProps) {
  return (
    <Link to={to} className={classNameFor(variant, size)}>
      {children}
    </Link>
  )
}
