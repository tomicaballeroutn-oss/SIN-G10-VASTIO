/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    // PWA mínima: se instala en el celular. La app necesita conexión: el service worker solo guarda
    // la interfaz (JS, CSS, fuentes, íconos), nunca respuestas de /api.
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['brand/icon-192.png'],
      manifest: {
        name: 'Vastio',
        short_name: 'Vastio',
        description: 'Agenda de eventos y control de existencias de bebida',
        lang: 'es-AR',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        orientation: 'any',
        background_color: '#e6e2dd',
        theme_color: '#153e6c',
        icons: [
          { src: '/brand/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: '/brand/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,woff2,png}'],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//],
      },
    }),
  ],
  server: {
    port: 5173,
    // El backend queda en el mismo origen: la cookie de refresco (SameSite=Strict, path /api/v1/auth) viaja sola.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
