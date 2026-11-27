import type { Meta, StoryObj } from '@storybook/react-vite'
import { Button } from '../Button/Button'
import { TextField } from '../TextField/TextField'
import { Toolbar } from './Toolbar'

const meta = {
  title: 'UI/Layout/Toolbar',
  component: Toolbar,
  args: {
    children: (
      <>
        <TextField label="Départ" placeholder="ex. Paris" />
        <TextField label="Arrivée" placeholder="ex. campus" />
        <TextField label="Date" type="date" />
        <Button variant="ghost">Effacer</Button>
      </>
    ),
  },
} satisfies Meta<typeof Toolbar>

export default meta
type Story = StoryObj<typeof meta>

export const SearchFilters: Story = {}
