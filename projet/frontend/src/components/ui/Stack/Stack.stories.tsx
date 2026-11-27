import type { Meta, StoryObj } from '@storybook/react-vite'
import { Card } from '../Card/Card'
import { Stack } from './Stack'

const meta = {
  title: 'UI/Layout/Stack',
  component: Stack,
  args: {
    children: (
      <>
        <Card>Premier</Card>
        <Card>Deuxième</Card>
        <Card>Troisième</Card>
      </>
    ),
  },
} satisfies Meta<typeof Stack>

export default meta
type Story = StoryObj<typeof meta>

export const Medium: Story = {}
export const Small: Story = { args: { gap: 'small' } }
