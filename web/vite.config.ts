import preact from '@preact/preset-vite'
import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8'))

// Für GitHub Pages: https://<user>.github.io/<repo>/ – per BASE_PATH überschreibbar
const base = process.env.BASE_PATH ?? '/3liga-sticker-tracker/'

export default defineConfig({
  base,
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  plugins: [
    preact(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icons/*.png', 'checklist.json', 'datenschutz.html'],
      manifest: {
        name: 'Sticker-Tracker',
        short_name: 'Sticker',
        description: 'Sammelalbum-Sticker per Kamera erfassen: Fehlende, Doppelte und Tauschliste im Blick.',
        lang: 'de',
        start_url: '.',
        scope: '.',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#121417',
        theme_color: '#121417',
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,png,svg,json}'],
        // Texterkennung (~7 MB) nicht vorab laden, sondern beim ersten Scan – danach aus dem Cache (offline)
        globIgnores: ['ocr/**'],
        runtimeCaching: [{
          urlPattern: ({ url }) => url.pathname.includes('/ocr/'),
          handler: 'CacheFirst',
          options: { cacheName: 'ocr', expiration: { maxEntries: 10 } },
        }],
        navigateFallback: 'index.html',
        navigateFallbackDenylist: [/datenschutz\.html$/],
      },
    }),
  ],
  test: { include: ['test/**/*.test.ts'] },
} as never)
