import { defineConfig } from '@playwright/test'
import base from './playwright.config'

/**
 * Same specs, longer timeouts, for a deployed free-tier instance (0.1 CPU:
 * registration hashes a password and ten recovery codes with bcrypt, which the
 * local 5-second assertion default is too short for).
 *   PLAYWRIGHT_BASE_URL=https://<service>.onrender.com npx playwright test -c playwright.public.config.ts <spec>
 * Uses fictional data only; mind the registration rate limit (RATE_LIMIT_REGISTER_PER_HOUR).
 */
export default defineConfig({
  ...base,
  timeout: 180_000,
  expect: { timeout: 45_000 },
  use: { ...base.use, actionTimeout: 45_000, navigationTimeout: 60_000 },
})
