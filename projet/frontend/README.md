# 5 — Covoiturage

Un conducteur publie un trajet, les passagers réservent une place. Impossible de réserver son propre trajet ou un trajet passé ; une seule place par passager et par trajet.

Application React (TypeScript strict, Vite, React Router, Sass). Les appels à l'API sont **bouchonnés** dans `src/mocks/api.ts` : l'application fonctionne seule, sans backend. Les données de démonstration (`src/mocks/data.ts`) sont en mémoire et réinitialisées à chaque rechargement.

Spécification de l'API : [`project/CLAUDE.md`](../../../CLAUDE.md), §5.

## Démarrer

Version de Node : **26.13.1** (fichier `.nvmrc`), avec npm 11.20 ; Vite 8.3, React 19.3 et React Router 8.5.

```bash
nvm use            # lit .nvmrc (nvm install si la version manque)
npm install
npm run dev        # http://localhost:5105
```

| Commande | Effet |
|---|---|
| `npm run dev` | Serveur de développement sur le port 5105 |
| `npm run build` | Vérification TypeScript (strict) puis build Vite dans `dist/` |
| `npm run preview` | Sert le build de `dist/` sur le port 5105 |
| `npm run lint:styles` | Vérifie que la feuille Sass `src/styles/main.scss` compile |

Avec Docker, depuis ce dossier : `docker compose up --build -d` (même port), `docker compose down` pour arrêter.

## Comptes de démonstration

Rappelés sur la page de connexion (bouton « Utiliser »).

| E-mail | Mot de passe | Rôle |
|---|---|---|
| admin@example.com | admin123 | ADMIN |
| alice@example.com | alice123 | USER |
| bob@example.com | bob123 | USER |
| chloe@example.com | chloe123 | USER |

## Routes

Toutes les routes, sauf `/login`, exigent d'être connecté. Les routes **ADMIN** redirigent un utilisateur simple vers l'accueil.

| Route | Page | Accès | Description |
|---|---|---|---|
| `/login` | `LoginPage` | Public | Connexion |
| `/` | `SearchPage` | Connecté | Recherche de trajets par date et par ville |
| `/new` | `TripFormPage` | Connecté | Publication d'un trajet |
| `/trips/:id` | `TripDetailPage` | Connecté | Détail d'un trajet : réservation, annulation |
| `/trips/:id/edit` | `TripFormPage` | Connecté | Modification de son trajet |
| `/me` | `MyTripsPage` | Connecté | Mes trajets (conducteur et passager) |
| `/admin` | `CarpoolAdminPage` | ADMIN | Trajets du mois, suppression (modération) |

## Endpoints bouchonnés

Mêmes routes, DTO, règles métier et format d'erreur que l'API Spring Boot.

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/carpool/trips`
- `GET /api/carpool/trips?from=&to=&date=` — trajets à venir, actifs.
- `GET /api/carpool/trips/{id}`
- `PUT /api/carpool/trips/{id}` — conducteur ou ADMIN ; pas moins de places que de réservations.
- `POST /api/carpool/trips/{id}/cancel`
- `POST /api/carpool/trips/{id}/bookings`
- `DELETE /api/carpool/trips/{id}/bookings/me`
- `GET /api/carpool/me/trips?role=driver|passenger`
- `GET /api/carpool/admin/trips?month=YYYY-MM` — ADMIN

## Organisation

```
src/
├── main.tsx          # point d'entrée
├── App.tsx           # contexte d'authentification, en-tête, routes
├── pages/            # une page par route
├── mocks/api.ts      # API bouchonnée (mockApi)
├── mocks/data.ts     # données de démonstration
└── styles/main.scss  # feuille de style unique
```
