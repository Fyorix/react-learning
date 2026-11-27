import type { Meta, StoryObj } from '@storybook/react-vite'
import { Button } from '../Button/Button'
import { Row } from './Row'

const meta = {
  title: 'UI/Layout/Row',
  component: Row,
  args: {
    children: (
      <>
        <Button variant="ghost">Annuler</Button>
        <Button variant="primary">Publier</Button>
      </>
    ),
  },
} satisfies Meta<typeof Row>

export default meta
type Story = StoryObj<typeof meta>

export const Start: Story = {}
export const End: Story = { args: { justify: 'end' } }
export const Between: Story = { args: { justify: 'between' } }
