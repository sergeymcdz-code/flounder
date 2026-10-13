/** Workbox serializes this RegExp into the service worker; documents must stay network-only. */
export const publicApiCachePattern = /^https?:\/\/[^/]+\/api\/v1\/public\/(?!documents(?:[/?#]|$))/
