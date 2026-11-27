import type { Meta, StoryObj } from '@storybook/react-vite'
import { Badge } from './Badge'

const meta = {
  title: 'UI/Badge',
  component: Badge,
  args: { children: 'passé' },
} satisfies Meta<typeof Badge>

export default meta
type Story = StoryObj<typeof meta>

export const Neutral: Story = {}
export const Accent: Story = { args: { tone: 'accent', children: 'votre trajet' } }
export const Success: Story = { args: { tone: 'success', children: 'réservé' } }
export const Warning: Story = { args: { tone: 'warning', children: 'bientôt complet' } }
export const Danger: Story = { args: { tone: 'danger', children: 'complet' } }
export const Info: Story = { args: { tone: 'info', children: '2 place(s)' } }

export const AfterText: Story = {
  render: () => (
    <span>
      Admin Démo<Badge tone="accent">admin</Badge>
    </span>
  ),
}
