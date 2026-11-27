import type { Meta, StoryObj } from '@storybook/react-vite'
import { Card } from './Card'

const meta = {
  title: 'UI/Card',
  component: Card,
  args: { children: 'Contenu de la carte' },
} satisfies Meta<typeof Card>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const WithTitle: Story = { args: { title: 'Passagers', children: 'Aucune réservation pour l’instant.' } }
export const Compact: Story = { args: { padding: 'compact' } }
export const Clickable: Story = { args: { to: '/trips/t-1', children: 'Survolez-moi : la carte entière est un lien' } }
