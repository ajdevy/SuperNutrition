/** Surface B — the product REST API (docs/06 §6.6). */
export const PRODUCT_ROUTES = [
  { method: 'POST', path: '/api/v1/auth/session' },
  { method: 'POST', path: '/api/v1/auth/refresh' },
  { method: 'POST', path: '/api/v1/auth/logout' },
  { method: 'DELETE', path: '/api/v1/auth/sessions' },

  { method: 'POST', path: '/api/v1/meals/photo' },
  { method: 'POST', path: '/api/v1/meals/manual' },
  { method: 'POST', path: '/api/v1/meals/voice' },
  { method: 'GET', path: '/api/v1/meals' },
  { method: 'GET', path: '/api/v1/meals/:id' },
  { method: 'PATCH', path: '/api/v1/meals/:id' },
  { method: 'DELETE', path: '/api/v1/meals/:id' },

  { method: 'GET', path: '/api/v1/goals' },
  { method: 'PATCH', path: '/api/v1/goals' },

  { method: 'GET', path: '/api/v1/journey' },
  { method: 'GET', path: '/api/v1/progress' },
  { method: 'GET', path: '/api/v1/progress/weekly' },
  { method: 'GET', path: '/api/v1/progress/trends' },
  { method: 'GET', path: '/api/v1/achievements' },
  { method: 'GET', path: '/api/v1/coach/messages' },

  { method: 'GET', path: '/api/v1/users/me' },
  { method: 'PATCH', path: '/api/v1/users/me' },
  { method: 'GET', path: '/api/v1/users/me/consents' },
  { method: 'POST', path: '/api/v1/users/me/export' },
  { method: 'DELETE', path: '/api/v1/users/me' },

  { method: 'GET', path: '/api/v1/catalog/search' },
  { method: 'GET', path: '/api/v1/catalog/items/:foodId' },
  { method: 'GET', path: '/api/v1/catalog/packages' },
] as const;
