import styles from './Loader.module.scss'

export interface LoaderProps {
  label?: string
}

/** Indicateur de chargement avec libellé, annoncé aux lecteurs d'écran. */
export function Loader({ label = 'Chargement…' }: LoaderProps) {
  return (
    <div className={styles.loader} role="status">
      {label}
    </div>
  )
}
