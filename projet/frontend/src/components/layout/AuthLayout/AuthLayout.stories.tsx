import type { Meta, StoryObj } from '@storybook/react-vite'
import { Button, Stack, TextField } from '../../ui'
import { AuthLayout } from './AuthLayout'

const meta = {
  title: 'Layout/AuthLayout',
  component: AuthLayout,
  parameters: { layout: 'fullscreen' },
  args: {
    title: 'Covoiturage',
    subtitle: 'Trajets et réservation de places',
    children: (
      <Stack>
        <TextField label="Email" type="email" />
        <TextField label="Mot de passe" type="password" />
        <Button variant="primary" type="submit">
          Se connecter
        </Button>
      </Stack>
    ),
  },
} satisfies Meta<typeof AuthLayout>

export default meta
type Story = StoryObj<typeof meta>

export const Login: Story = {}
