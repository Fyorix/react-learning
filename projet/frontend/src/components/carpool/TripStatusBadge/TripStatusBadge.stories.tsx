import type { Meta, StoryObj } from '@storybook/react-vite'
import { TripStatusBadge } from './TripStatusBadge'

const meta = {
  title: 'Covoiturage/TripStatusBadge',
  component: TripStatusBadge,
  args: { trip: { status: 'ACTIVE', past: false } },
} satisfies Meta<typeof TripStatusBadge>

export default meta
type Story = StoryObj<typeof meta>

export const Upcoming: Story = {}
export const Past: Story = { args: { trip: { status: 'ACTIVE', past: true } } }
export const Cancelled: Story = { args: { trip: { status: 'CANCELLED', past: false } } }
export const ModerationActive: Story = { args: { view: 'moderation', trip: { status: 'ACTIVE', past: true } } }
