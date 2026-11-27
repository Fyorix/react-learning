import type { Meta, StoryObj } from '@storybook/react-vite'
import { fn } from 'storybook/test'
import { Card } from '../../ui'
import { AppShell } from './AppShell'

const links = [
  { to: '/', label: 'Rechercher', end: true },
  { to: '/new', label: 'Proposer un trajet' },
  { to: '/me', label: 'Mes trajets' },
]

const meta = {
  title: 'Layout/AppShell',
  component: AppShell,
  parameters: { layout: 'fullscreen' },
  args: {
    brand: 'Covoiturage',
    links,
    userName: 'Alice Martin',
    isAdmin: false,
    onLogout: fn(),
    children: <Card>Contenu de la page</Card>,
  },
} satisfies Meta<typeof AppShell>

export default meta
type Story = StoryObj<typeof meta>

export const User: Story = {}
export const Admin: Story = {
  args: { userName: 'Admin Démo', isAdmin: true, links: [...links, { to: '/admin', label: 'Administration' }] },
}
