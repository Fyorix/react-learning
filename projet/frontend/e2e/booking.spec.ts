import { expect, test, type Page } from '@playwright/test'

async function login(page: Page, email: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Mot de passe').fill(password)
  await page.getByRole('button', { name: 'Se connecter' }).click()
}

test('redirige vers la connexion sans être connecté', async ({ page }) => {
  await page.goto('/me')
  await expect(page).toHaveURL('/login')
})

test('refuse un mot de passe incorrect', async ({ page }) => {
  await login(page, 'chloe@example.com', 'mauvais')
  await expect(page.getByText('Email ou mot de passe incorrect')).toBeVisible()
  await expect(page).toHaveURL('/login')
})

test('un passager réserve puis annule une place', async ({ page }) => {
  await login(page, 'chloe@example.com', 'chloe123')
  await expect(page.getByRole('heading', { name: 'Trouver un trajet' })).toBeVisible()

  await page.getByLabel('Départ').fill('Lyon')
  await page.getByRole('link', { name: /Lyon.*Paris/ }).click()
  await expect(page).toHaveURL(/\/trips\//)

  await page.getByRole('button', { name: 'Réserver une place' }).click()
  await expect(page.getByText('Place réservée. Bon voyage !')).toBeVisible()

  await page.getByRole('button', { name: 'Annuler ma réservation' }).click()
  await expect(page.getByText('Réservation annulée.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Réserver une place' })).toBeVisible()
})
