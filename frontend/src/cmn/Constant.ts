// Empty by default so API calls go to the same origin ("/api/...").
// - Local: the CRA dev server proxies "/api" to the backend (see "proxy" in package.json).
// - AWS: route "/api" to the backend on the same domain (ALB / reverse proxy).
// Set REACT_APP_API_BASE_URL at build time only when the API is served from a different origin.
export const API_BASE_URL = process.env.REACT_APP_API_BASE_URL ?? "";
