import type { Meta, StoryObj } from '@storybook/react-vite'
import { DescriptionList } from './DescriptionList'

const meta = {
  title: 'UI/DescriptionList',
  component: DescriptionList,
  args: {
    items: [
      { term: 'Départ', description: 'samedi 28 novembre à 08:00', strong: true },
      { term: 'Conducteur', description: 'Alice Martin' },
      { term: 'Places', description: '2 restante(s) sur 3' },
      { term: 'Commentaire', description: 'Départ métro Place d’Italie' },
    ],
  },
} satisfies Meta<typeof DescriptionList>

export default meta
type Story = StoryObj<typeof meta>

export const TripDetails: Story = {}
