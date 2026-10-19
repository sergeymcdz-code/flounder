/** Workbox serializes this RegExp; documents and PDF protocols must stay network-only. */
export const publicApiCachePattern = /^https?:\/\/[^/]+\/api\/v1\/public\/(?!organizations\/[^/?#]+\/logo(?:[/?#]|$))(?!documents(?:[/?#]|$))(?![^?#]*\.pdf(?:[?#]|$))/i
