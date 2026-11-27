import type { Meta, StoryObj } from '@storybook/react-vite'
import { Kpi, KpiGrid } from './Kpi'

const meta = {
  title: 'UI/Kpi',
  component: Kpi,
  args: { value: 12, label: 'trajets' },
} satisfies Meta<typeof Kpi>

export default meta
type Story = StoryObj<typeof meta>

export const Single: Story = {}
export const NoData: Story = { args: { value: '—', label: 'taux de remplissage' } }

export const Grid: Story = {
  render: () => (
    <KpiGrid>
      <Kpi value={12} label="trajets" />
      <Kpi value={2} label="annulés" />
      <Kpi value="67 %" label="taux de remplissage" />
    </KpiGrid>
  ),
}
