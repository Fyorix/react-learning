import type { Meta, StoryObj } from '@storybook/react-vite'
import { Card } from '../Card/Card'
import { Section } from './Section'

const meta = {
  title: 'UI/Layout/Section',
  component: Section,
  args: { children: <Card title="Passagers">Aucune réservation pour l’instant.</Card> },
  decorators: [
    (Story) => (
      <>
        <Card>Bloc précédent</Card>
        <Story />
      </>
    ),
  ],
} satisfies Meta<typeof Section>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
