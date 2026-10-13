import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type Plugin } from 'vite'
import { rm } from 'node:fs/promises'
import { resolve } from 'node:path'
import { VitePWA } from 'vite-plugin-pwa'

function excludeMockWorker(): Plugin {
  let outputDirectory = ''
  return {
    name: 'exclude-production-mock-worker',
    apply: 'build',
    configResolved(config) {
      outputDirectory = resolve(config.root, config.build.outDir)
    },
    async writeBundle() {
      // The development worker is served from public/, but must not be deployed.
      await rm(resolve(outputDirectory, 'mockServiceWorker.js'), {
        force: true,
      })
    },
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const mocksEnabled = (process.env.VITE_API_MOCK ?? env.VITE_API_MOCK) === 'true'
  return {
    plugins: [
      react(),
      excludeMockWorker(),
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
          theme_color: '#182126',
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
                networkTimeoutSeconds: 7,
                cacheableResponse: { statuses: [200] },
                expiration: { maxEntries: 80, maxAgeSeconds: 3600 },
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
        disable: mocksEnabled,
      }),
    ],
  }
})
