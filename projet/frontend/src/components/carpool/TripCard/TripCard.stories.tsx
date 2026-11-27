import type { Meta, StoryObj } from '@storybook/react-vite'
import { tripFixture } from '../fixtures'
import { TripCard } from './TripCard'

const meta = {
  title: 'Covoiturage/TripCard',
  component: TripCard,
  args: { trip: tripFixture },
} satisfies Meta<typeof TripCard>

export default meta
type Story = StoryObj<typeof meta>

export const Available: Story = {}
export const Booked: Story = { args: { trip: { ...tripFixture, booked: true } } }
export const Full: Story = { args: { trip: { ...tripFixture, remainingSeats: 0 } } }
export const OwnTrip: Story = { args: { trip: { ...tripFixture, isDriver: true } } }
