# Deploying OneStopSports (free)

This deploys the whole app — React frontend + Spring Boot API + WebSocket live
scores — as **one** free service, backed by a free, durable Postgres database.

- **Backend + frontend** → Render (free Docker web service)
- **Database** → Neon (free Postgres that doesn't expire)
- **Redis** → not used in production (the `prod` profile uses an in-memory cache)

**The one tradeoff:** on Render's free tier the service sleeps after ~15 min of
no traffic, so the first visit after idle takes ~30–60s to wake. Fine for a
portfolio link. (Upgrading to Render's $7/mo Starter plan removes the sleep.)

---

## How it works (what the deploy prep changed)

The frontend already uses relative paths (`/api`, `/ws`), so the Dockerfile now
builds the React app and **embeds it inside the Spring Boot jar**. One container
serves both the site and the API from the same origin — no CORS, and WebSockets
upgrade to `wss://` automatically on HTTPS. The `prod` Spring profile points the
database at Neon, disables Redis, and binds to the port Render assigns.

---

## Step 1 — Create the database on Neon (~3 min)

1. Go to <https://neon.tech> and sign up (GitHub login is easiest).
2. **Create a project** — name it `onestopsports`. Pick the region closest to
   you. It creates a database (default name `neondb` is fine).
3. On the project dashboard, find **Connection string** and copy it. It looks like:
   ```
   postgresql://alex:AbCd1234@ep-cool-name-12345.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```
4. Break that one string into the three values Render will need:
   - **SPRING_DATASOURCE_URL** → take the connection string, change the scheme
     from `postgresql://` to `jdbc:postgresql://`, and **remove the
     `user:password@` part**. Keep `?sslmode=require`. Result:
     ```
     jdbc:postgresql://ep-cool-name-12345.us-east-2.aws.neon.tech/neondb?sslmode=require
     ```
   - **SPRING_DATASOURCE_USERNAME** → the user before the `:` (e.g. `alex`)
   - **SPRING_DATASOURCE_PASSWORD** → the part between `:` and `@` (e.g. `AbCd1234`)

   > Keep `?sslmode=require` — Neon refuses non-SSL connections.

---

## Step 2 — Gather the other two secrets

- **FOOTBALL_DATA_API_KEY** — the key already in your local `.env`
  (`FOOTBALL_DATA_API_KEY=...`). If you need a fresh one, register free at
  <https://www.football-data.org/client/register>.
- **JWT_SECRET** — generate a new one in your terminal:
  ```bash
  openssl rand -base64 64
  ```
  Copy the whole output (it's fine that it spans lines — paste it as one value).

---

## Step 3 — Deploy on Render (~5 min)

1. Go to <https://render.com> and sign up (GitHub login).
2. **New → Blueprint**, then connect your GitHub and pick the
   `Jstrine09/OneStopSports` repo. Render reads `render.yaml` and proposes an
   `onestopsports` web service.
   - (If you prefer not to use the Blueprint: **New → Web Service**, pick the
     repo, choose **Docker**, set Health Check Path to `/api/sports`, and add the
     env vars below manually.)
3. When prompted, fill in the environment variables:

   | Key | Value |
   |-----|-------|
   | `SPRING_PROFILES_ACTIVE` | `prod` (pre-filled by the blueprint) |
   | `SPRING_DATASOURCE_URL` | the `jdbc:postgresql://…?sslmode=require` from Step 1 |
   | `SPRING_DATASOURCE_USERNAME` | Neon user |
   | `SPRING_DATASOURCE_PASSWORD` | Neon password |
   | `FOOTBALL_DATA_API_KEY` | your football-data.org key |
   | `JWT_SECRET` | the `openssl rand` output |

4. Click **Apply / Create**. Render builds the Docker image (first build ~5–8 min).

---

## Step 4 — First boot

On the first start, Flyway creates the schema and the data loaders seed teams,
players, and leagues from the sports APIs (~2 min, rate-limited). The site is
reachable the moment the server is up; sports data fills in shortly after. Watch
**Logs** in Render for `Started OneStopSportsApplication` and the seeding lines.

Your live URL will be something like `https://onestopsports.onrender.com`.

---

## Updating later

Render auto-deploys on every push to the connected branch. Push to that branch
and Render rebuilds and redeploys automatically.

## Troubleshooting

- **App won't start, DB errors** → re-check `SPRING_DATASOURCE_URL` starts with
  `jdbc:postgresql://`, has no `user:password@`, and keeps `?sslmode=require`.
- **401s on the site itself** → make sure `SPRING_PROFILES_ACTIVE=prod` is set.
- **Empty leagues/teams** → a sports API blip during seeding; click
  **Manual Deploy → Clear build cache & deploy**, or just restart, to re-seed.
- **Slow first load** → expected on free tier (cold start). Subsequent loads are
  fast until it idles again.
