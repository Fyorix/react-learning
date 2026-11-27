import type { Meta, StoryObj } from '@storybook/react-vite'
import { fn } from 'storybook/test'
import { Pagination } from './Pagination'

const meta = {
  title: 'UI/Pagination',
  component: Pagination,
  args: { page: 1, totalPages: 4, onPageChange: fn() },
} satisfies Meta<typeof Pagination>

export default meta
type Story = StoryObj<typeof meta>

export const Middle: Story = {}
export const FirstPage: Story = { args: { page: 0 } }
export const LastPage: Story = { args: { page: 3 } }
export const SinglePage: Story = { args: { page: 0, totalPages: 1 } }
