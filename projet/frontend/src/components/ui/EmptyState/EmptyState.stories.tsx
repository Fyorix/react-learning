import type { Meta, StoryObj } from '@storybook/react-vite'
import { Link } from 'react-router'
import { EmptyState } from './EmptyState'

const meta = {
  title: 'UI/EmptyState',
  component: EmptyState,
  args: { children: 'Aucun trajet ce mois-ci.' },
} satisfies Meta<typeof EmptyState>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const WithAction: Story = {
  args: {
    children: (
      <>
        Vous n’avez proposé aucun trajet. <Link to="/new">En proposer un</Link>
      </>
    ),
  },
}
