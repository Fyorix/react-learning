import type { Meta, StoryObj } from '@storybook/react-vite'
import { TextAreaField, TextField } from './TextField'

const meta = {
  title: 'UI/TextField',
  component: TextField,
  args: { label: 'Départ', placeholder: 'ex. Paris' },
} satisfies Meta<typeof TextField>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const WithError: Story = { args: { defaultValue: '', error: 'La ville de départ est obligatoire' } }
export const WithHint: Story = { args: { hint: 'Ville ou adresse précise' } }
export const DateInput: Story = { args: { label: 'Date', type: 'date', placeholder: undefined } }
export const NumberInput: Story = { args: { label: 'Places proposées (1 à 8)', type: 'number', min: 1, max: 8, defaultValue: 3 } }

export const TextArea: Story = {
  render: () => <TextAreaField label="Commentaire (facultatif)" placeholder="Point de rendez-vous, bagages, musique…" />,
}
