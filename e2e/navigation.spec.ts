import { test, expect } from "@playwright/test";

test("navigue de l'accueil vers À propos", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Accueil" })).toBeVisible();
  await page.getByRole("link", { name: "À propos" }).click();
  await expect(page).toHaveURL("/about");
  await expect(page.getByRole("heading", { name: "À propos" })).toBeVisible();
});

test("affiche la page 404 sur une URL inconnue", async ({ page }) => {
  await page.goto("/n-existe-pas");
  await expect(page.getByRole("heading", { name: "Page introuvable" })).toBeVisible();
});
