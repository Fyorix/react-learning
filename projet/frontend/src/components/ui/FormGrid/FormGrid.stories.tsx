import type { Meta, StoryObj } from '@storybook/react-vite'
import { TextAreaField, TextField } from '../TextField/TextField'
import { FormGrid } from './FormGrid'

const meta = {
  title: 'UI/Layout/FormGrid',
  component: FormGrid,
  args: {
    children: (
      <>
        <TextField label="Départ" />
        <TextField label="Arrivée" />
        <TextField label="Date et heure de départ" type="datetime-local" />
        <TextField label="Places proposées (1 à 8)" type="number" defaultValue={3} />
        <TextAreaField label="Commentaire (facultatif)" fullWidth />
      </>
    ),
  },
} satisfies Meta<typeof FormGrid>

export default meta
type Story = StoryObj<typeof meta>

export const TripForm: Story = {}
