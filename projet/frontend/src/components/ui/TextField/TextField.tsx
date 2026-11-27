import { useId, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react'
import styles from './TextField.module.scss'

interface FieldProps {
  label: string
  /** Message d'erreur de validation, affiché sous le champ. */
  error?: string
  hint?: string
  /** Occupe toute la largeur d'une grille de formulaire. */
  fullWidth?: boolean
}

function Field({ label, hint, fullWidth, children }: Omit<FieldProps, 'error'> & { children: ReactNode }) {
  return (
    <label className={fullWidth ? `${styles.field} ${styles.full}` : styles.field}>
      <span className={styles.label}>{label}</span>
      {children}
      {hint && <span className={styles.hint}>{hint}</span>}
    </label>
  )
}

function useErrorProps(error: string | undefined) {
  const id = useId()
  const message = error ? (
    <span id={id} className={styles.error}>
      {error}
    </span>
  ) : null
  const aria = error ? { 'aria-invalid': true, 'aria-describedby': id } : {}
  return { message, aria }
}

export type TextFieldProps = FieldProps & Omit<InputHTMLAttributes<HTMLInputElement>, 'className' | 'style'>

/** Champ de saisie avec libellé et message d'erreur. Accepte tous les types d'`<input>`. */
export function TextField({ label, error, hint, fullWidth, ...input }: TextFieldProps) {
  const { message, aria } = useErrorProps(error)
  return (
    <Field label={label} hint={hint} fullWidth={fullWidth}>
      <input {...input} {...aria} className={styles.control} />
      {message}
    </Field>
  )
}

export type TextAreaFieldProps = FieldProps & Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'className' | 'style'>

/** Zone de texte multiligne avec libellé et message d'erreur. */
export function TextAreaField({ label, error, hint, fullWidth, ...textarea }: TextAreaFieldProps) {
  const { message, aria } = useErrorProps(error)
  return (
    <Field label={label} hint={hint} fullWidth={fullWidth}>
      <textarea {...textarea} {...aria} className={styles.control} />
      {message}
    </Field>
  )
}
