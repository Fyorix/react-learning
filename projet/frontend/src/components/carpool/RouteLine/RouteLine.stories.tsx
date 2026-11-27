import type { Meta, StoryObj } from '@storybook/react-vite'
import { RouteLine } from './RouteLine'

const meta = {
  title: 'Covoiturage/RouteLine',
  component: RouteLine,
  args: { from: 'Paris 13e', to: 'Kremlin-Bicêtre (campus)' },
} satisfies Meta<typeof RouteLine>

export default meta
type Story = StoryObj<typeof meta>

export const Medium: Story = {}
export const Large: Story = { args: { size: 'large' } }
export const LongNames: Story = {
  args: { from: 'Saint-Germain-en-Laye (gare RER)', to: 'Kremlin-Bicêtre, campus EPITA, entrée principale' },
}
