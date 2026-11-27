import type { DemoAccount } from '../../../api/types'
import { Badge, Button, Table, Text, type TableColumn } from '../../ui'
import styles from './DemoAccounts.module.scss'

export interface DemoAccountsProps {
  accounts: DemoAccount[]
  /** Appelé avec le compte choisi pour pré-remplir le formulaire de connexion. */
  onSelect: (account: DemoAccount) => void
}

/** Liste des comptes de démonstration, avec un bouton pour les utiliser. */
export function DemoAccounts({ accounts, onSelect }: DemoAccountsProps) {
  const columns: TableColumn<DemoAccount>[] = [
    {
      key: 'name',
      header: 'Nom',
      render: (a) => (
        <>
          {a.displayName}
          {a.role === 'ADMIN' && <Badge tone="accent">admin</Badge>}
          {a.note && (
            <Text as="div" tone="muted" size="small">
              {a.note}
            </Text>
          )}
        </>
      ),
    },
    { key: 'email', header: 'Email', render: (a) => a.email },
    { key: 'password', header: 'Mot de passe', render: (a) => <code>{a.password}</code> },
    {
      key: 'use',
      header: '',
      render: (a) => (
        <Button variant="ghost" size="small" onClick={() => onSelect(a)}>
          Utiliser
        </Button>
      ),
    },
  ]

  return (
    <div className={styles.demo}>
      <h2 className={styles.title}>Comptes de démonstration</h2>
      <div className={styles.table}>
        <Table columns={columns} rows={accounts} rowKey={(a) => a.email} compact />
      </div>
      <Text as="p" tone="muted" size="small">
        Données en mémoire : elles sont réinitialisées à chaque rechargement de la page.
      </Text>
    </div>
  )
}
