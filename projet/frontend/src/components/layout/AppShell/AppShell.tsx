import type { ReactNode } from 'react'
import { NavLink } from 'react-router'
import { Badge, Button } from '../../ui'
import styles from './AppShell.module.scss'

export interface NavItem {
  to: string
  label: string
  /** Actif seulement sur la route exacte (pour `/`). */
  end?: boolean
}

export interface AppShellProps {
  brand: string
  links: NavItem[]
  userName: string
  isAdmin: boolean
  onLogout: () => void
  children: ReactNode
}

/** Gabarit des pages connectées : en-tête collant (marque, navigation, utilisateur) et contenu centré. */
export function AppShell({ brand, links, userName, isAdmin, onLogout, children }: AppShellProps) {
  return (
    <>
      <header className={styles.header}>
        <div className={styles.brand}>{brand}</div>
        <nav className={styles.nav} aria-label="Navigation principale">
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              end={l.end}
              className={({ isActive }) => (isActive ? `${styles.link} ${styles.active}` : styles.link)}
            >
              {l.label}
            </NavLink>
          ))}
        </nav>
        <div className={styles.user}>
          <span>{userName}</span>
          {isAdmin && <Badge tone="accent">admin</Badge>}
          <Button variant="ghost" size="small" onClick={onLogout}>
            Déconnexion
          </Button>
        </div>
      </header>
      <main className={styles.main}>{children}</main>
    </>
  )
}
