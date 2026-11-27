import type { Meta, StoryObj } from '@storybook/react-vite'
import { Alert } from './Alert'

const meta = {
  title: 'UI/Alert',
  component: Alert,
  args: { tone: 'info', children: 'Données en mémoire.' },
} satisfies Meta<typeof Alert>

export default meta
type Story = StoryObj<typeof meta>

export const ErrorMessage: Story = { args: { tone: 'error', children: 'Email ou mot de passe incorrect' } }
export const Success: Story = { args: { tone: 'success', children: 'Place réservée. Bon voyage !' } }
export const Info: Story = {}
export const Warning: Story = { args: { tone: 'warning', children: 'Ce trajet a été annulé par son conducteur.' } }
