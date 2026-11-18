# Deploying PulseWatch to a server

This puts PulseWatch on **one small Linux server** with a real domain and automatic HTTPS. It needs about 1 GB of RAM (2 GB is comfortable) and ten minutes.

> The production stack (`deploy/compose.prod.yaml`) was tested locally in Docker: it refuses to start without secrets, publishes only ports 80/443, refuses internal monitor URLs, rate limits logins and runs the API as a non-root user. It has **not** been run on a public server by its author, so expect to read the logs once.

## 1. Get a server and a domain

- **Server:** any Ubuntu 24.04 VPS. Cheap options: Hetzner Cloud (CX22, about 4 EUR/month), DigitalOcean, or Oracle Cloud's Always Free tier (an Ampere ARM instance: the images are multi-architecture, so they build there too).
- **Firewall:** allow inbound 22 (SSH), 80 and 443 (TCP and UDP). Nothing else.
- **Domain:** create an `A` record, for example `pulsewatch.example.com`, pointing at the server's IP. Caddy cannot get a certificate until this resolves.

## 2. Install Docker

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # log out and back in afterwards
```

## 3. Get the code and configure it

```bash
git clone <this repository> pulsewatch && cd pulsewatch/deploy
cp .env.example .env
nano .env
```

Fill in every line. Generate the two secrets with `openssl rand -base64 32`. For `SMTP_*` use any provider (Brevo and Mailgun have free tiers; Amazon SES is cheap). Use a sender address on a domain you control, and set up SPF/DKIM with your provider or alert emails land in spam.

## 4. Start it

```bash
docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml ps          # all services "Up" / "healthy"
docker compose -f compose.prod.yaml logs -f web # watch Caddy obtain the certificate
```

Open `https://your-domain`, create an account, add a monitor. Sign-up is open to anyone who finds the site; if you do not want that, put the app behind your own access control before announcing it.

## 5. Check it from outside

```bash
../scripts/smoke-test.sh https://your-domain
```

Its last block ("a monitor, end to end") uses the local httpbin container, which production does not have, so expect only that block to fail. Everything above it must pass, including "`/actuator/prometheus` is hidden".

## Operating it

| Task | Command (run in `deploy/`) |
|---|---|
| Logs | `docker compose -f compose.prod.yaml logs -f app` |
| Update to new code | `git pull && docker compose -f compose.prod.yaml up -d --build` (Flyway migrates the database on startup) |
| Restart | `docker compose -f compose.prod.yaml restart app` |
| Back up the database | `docker compose -f compose.prod.yaml exec -T postgres pg_dump -U pulsewatch pulsewatch \| gzip > backup-$(date +%F).sql.gz` |
| Restore | `gunzip -c backup.sql.gz \| docker compose -f compose.prod.yaml exec -T postgres psql -U pulsewatch pulsewatch` (into an empty database) |
| Stop everything | `docker compose -f compose.prod.yaml down` (add `-v` ONLY to delete the data) |

Schedule the backup with `cron` and copy the files off the server. Checks older than 7 days are rolled up and deleted automatically at 03:00 UTC, so the database stays small.

## Using the images built by CI instead of building on the server

Pushes to `main` publish `ghcr.io/<owner>/<repo>/app` and `/web`. In `compose.prod.yaml` replace each `build:` line with `image: ghcr.io/<owner>/<repo>/app:latest` (and `web`), run `docker login ghcr.io` on the server if the package is private, then `docker compose -f compose.prod.yaml pull && ... up -d`.

## Hardening checklist before inviting other people

- Keep the server patched (`unattended-upgrades`), disable SSH password login.
- Consider monitoring from a separate network or adding egress firewall rules for the API container: the SSRF guard checks addresses, but only a firewall fully closes DNS-rebinding tricks.
- Add the Prometheus/Grafana services from `../compose.yaml` only on a private network or behind authentication; never publish them.
- Rotate `JWT_SECRET` if it ever leaks (this signs everyone out).
