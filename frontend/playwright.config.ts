import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas de punta a punta contra el sistema levantado:
 *   docker compose up -d db
 *   cd backend; .\mvnw spring-boot:run      (con VASTIO_ADMIN_CONTRASENA en el .env)
 *   cd frontend; npm run dev
 *   cd frontend; $env:E2E_USUARIO='direccion'; $env:E2E_CONTRASENA='...'; npm run e2e
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    // Solo para probar el stack de producción en local (certificado de la CA interna de Caddy).
    ignoreHTTPSErrors: process.env.E2E_IGNORAR_CERTIFICADO === '1',
  },
  projects: [
    { name: 'escritorio', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
    { name: 'telefono', use: { ...devices['Pixel 7'] } },
  ],
});
