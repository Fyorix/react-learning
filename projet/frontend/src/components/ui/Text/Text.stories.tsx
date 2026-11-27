import type { Meta, StoryObj } from '@storybook/react-vite'
import { Text } from './Text'

const meta = {
  title: 'UI/Text',
  component: Text,
  args: { children: 'Données en mémoire : elles sont réinitialisées à chaque rechargement de la page.' },
} satisfies Meta<typeof Text>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const Muted: Story = { args: { tone: 'muted', as: 'p' } }
export const MutedSmall: Story = { args: { tone: 'muted', size: 'small', as: 'p' } }
export const Strong: Story = { args: { weight: 'strong', children: 'samedi 28 novembre à 08:00' } }
