import type { Meta, StoryObj } from '@storybook/react-vite'
import { SeatMap } from './SeatMap'

const meta = {
  title: 'Covoiturage/SeatMap',
  component: SeatMap,
  args: { seats: 3, remainingSeats: 2 },
} satisfies Meta<typeof SeatMap>

export default meta
type Story = StoryObj<typeof meta>

export const PartlyBooked: Story = {}
export const Empty: Story = { args: { remainingSeats: 3 } }
export const Full: Story = { args: { remainingSeats: 0 } }
export const LargeCar: Story = { args: { seats: 8, remainingSeats: 5 } }
