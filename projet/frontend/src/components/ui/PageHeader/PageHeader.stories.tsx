import type { Meta, StoryObj } from '@storybook/react-vite'
import { ButtonLink } from '../Button/Button'
import { PageHeader } from './PageHeader'

const meta = {
  title: 'UI/PageHeader',
  component: PageHeader,
  args: { title: 'Trouver un trajet', description: 'Partagez la route avec vos collègues.' },
} satisfies Meta<typeof PageHeader>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const TitleOnly: Story = { args: { title: 'Proposer un trajet', description: undefined } }
export const WithAction: Story = {
  args: { actions: <ButtonLink to="/new" variant="primary">Proposer un trajet</ButtonLink> },
}
