import type { Meta, StoryObj } from '@storybook/react-vite'
import { Card } from '../Card/Card'
import { NarrowContainer } from './NarrowContainer'

const meta = {
  title: 'UI/Layout/NarrowContainer',
  component: NarrowContainer,
  args: { children: <Card>Contenu limité à 760 px de large</Card> },
  parameters: { layout: 'fullscreen' },
} satisfies Meta<typeof NarrowContainer>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
