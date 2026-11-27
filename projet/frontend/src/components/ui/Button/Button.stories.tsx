import type { Meta, StoryObj } from '@storybook/react-vite'
import { fn } from 'storybook/test'
import { Button, ButtonLink } from './Button'

const meta = {
  title: 'UI/Button',
  component: Button,
  args: { children: 'Réserver une place', onClick: fn() },
} satisfies Meta<typeof Button>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const Primary: Story = { args: { variant: 'primary' } }
export const Danger: Story = { args: { variant: 'danger', children: 'Annuler le trajet' } }
export const Ghost: Story = { args: { variant: 'ghost', children: 'Effacer' } }
export const Small: Story = { args: { size: 'small', children: 'Suivant' } }
export const Disabled: Story = { args: { variant: 'primary', disabled: true, children: 'Complet' } }

export const AsLink: Story = {
  render: () => (
    <ButtonLink to="/new" variant="primary">
      Proposer un trajet
    </ButtonLink>
  ),
}
