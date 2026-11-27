import { Button } from '../Button/Button'
import styles from './Pagination.module.scss'

export interface PaginationProps {
  /** Page courante, numérotée à partir de 0 comme dans l'API. */
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}

/** Navigation précédent / suivant. Ne s'affiche pas s'il n'y a qu'une page. */
export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) return null
  return (
    <nav className={styles.pagination} aria-label="Pagination">
      <Button size="small" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
        Précédent
      </Button>
      <span>
        Page {page + 1} / {totalPages}
      </span>
      <Button size="small" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
        Suivant
      </Button>
    </nav>
  )
}
