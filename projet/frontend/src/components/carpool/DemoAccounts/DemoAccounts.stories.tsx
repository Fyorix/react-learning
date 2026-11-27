import type { Meta, StoryObj } from '@storybook/react-vite'
import { fn } from 'storybook/test'
import { demoAccountsFixture } from '../fixtures'
import { DemoAccounts } from './DemoAccounts'

const meta = {
  title: 'Covoiturage/DemoAccounts',
  component: DemoAccounts,
  args: { accounts: demoAccountsFixture, onSelect: fn() },
} satisfies Meta<typeof DemoAccounts>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const Empty: Story = { args: { accounts: [] } }
