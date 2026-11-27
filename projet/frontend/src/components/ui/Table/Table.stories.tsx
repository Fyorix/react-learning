import type { Meta, StoryObj } from '@storybook/react-vite'
import { Badge } from '../Badge/Badge'
import { Table, type TableColumn } from './Table'

interface Row {
  id: string
  date: string
  route: string
  seats: string
  cancelled: boolean
}

const rows: Row[] = [
  { id: '1', date: '28/11/2026 08:00', route: 'Paris 13e → Kremlin-Bicêtre (campus)', seats: '1 / 3', cancelled: false },
  { id: '2', date: '29/11/2026 09:00', route: 'Ivry-sur-Seine → Villejuif', seats: '1 / 1', cancelled: false },
  { id: '3', date: '30/11/2026 08:00', route: 'Paris 5e → Kremlin-Bicêtre (campus)', seats: '0 / 2', cancelled: true },
]

const columns: TableColumn<Row>[] = [
  { key: 'date', header: 'Départ', render: (r) => r.date, nowrap: true },
  { key: 'route', header: 'Trajet', render: (r) => r.route },
  { key: 'seats', header: 'Places', render: (r) => r.seats },
  {
    key: 'status',
    header: 'État',
    render: (r) => (r.cancelled ? <Badge tone="danger">annulé</Badge> : <Badge tone="success">actif</Badge>),
  },
]

const meta = {
  title: 'UI/Table',
  component: Table<Row>,
  args: { columns, rows, rowKey: (r: Row) => r.id },
} satisfies Meta<typeof Table<Row>>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const Compact: Story = { args: { compact: true } }
export const Empty: Story = { args: { rows: [] } }
