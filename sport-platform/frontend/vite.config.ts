import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig(({ mode }) => ({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: null,
      includeAssets: [
        'icons/flounder-192.png',
        'icons/flounder-512.png',
        'favicon.svg',
      ],
      manifest: {
        name: 'Flounder',
        short_name: 'Flounder',
        lang: 'ru',
        description:
          'Расписание, заплывы и результаты соревнований по плаванию',
        theme_color: '#0b5b7b',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        icons: [
          {
            src: '/icons/flounder-192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any',
          },
          {
            src: '/icons/flounder-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any',
          },
          {
            src: '/icons/flounder-maskable-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,png,svg,woff2}'],
        globIgnores: ['**/mockServiceWorker.js'],
        navigateFallback: '/index.html',
        navigateFallbackAllowlist: [/^\/$/, /^\/e\//, /^\/events\/[^/]+\/?$/],
        cleanupOutdatedCaches: true,
        runtimeCaching: [
          {
            urlPattern: ({ url }) => url.pathname.startsWith('/api/v1/public/'),
            handler: 'NetworkFirst',
            method: 'GET',
            options: {
              cacheName: 'flounder-public-api-v1',
              networkTimeoutSeconds: 3,
              cacheableResponse: { statuses: [200] },
              expiration: { maxEntries: 80, maxAgeSeconds: 86_400 },
              plugins: [
                {
                  // Mark cached fallback data even when navigator.onLine is still true.
                  cachedResponseWillBeUsed: async ({ cachedResponse }) => {
                    if (!cachedResponse) return undefined
                    const headers = new Headers(cachedResponse.headers)
                    headers.set('X-Flounder-Cached', 'true')
                    return new Response(cachedResponse.body, {
                      status: cachedResponse.status,
                      statusText: cachedResponse.statusText,
                      headers,
                    })
                  },
                },
              ],
            },
          },
          {
            urlPattern: ({ url }) => /^\/(assets|icons)\//.test(url.pathname),
            handler: 'CacheFirst',
            options: {
              cacheName: 'flounder-static-v1',
              expiration: { maxEntries: 80, maxAgeSeconds: 2_592_000 },
            },
          },
        ],
      },
      // MSW and the PWA worker have the same root scope. Use one worker per mode.
      disable: mode === 'demo',
    }),
  ],
}))
