# React — EPITA ING2

| Dossier | Contenu |
|---|---|
| `training/` | TP d'entraînement : Vite + React Router + Storybook + Playwright |
| `projet/frontend/` | Projet n° 5 — Covoiturage (front React) |
| `projet/backend/` | API Spring Boot fournie (ne pas modifier) |

Lancer le backend :

```bash
cd projet/backend
cp .env.example .env
docker compose up --build -d   # http://localhost:8080/swagger-ui.html
```

Lancer le front :

```bash
cd projet/frontend
nvm use && npm install
npm run dev                    # http://localhost:5105
```
