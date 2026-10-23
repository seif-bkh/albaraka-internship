export const environment = {
  production: false,
  // Relative URL: requests go to the origin serving the app, which proxies
  // /api to the backend (nginx in Docker, the Angular dev-server proxy locally).
  // No host or port is hardcoded anywhere in the app.
  apiUrl: '/api',
  // Prayer times — Aladhan API, official Tunisia method (18), default city
  // Tunis. See docs/UIUX_RESEARCH.md §5. The widget fails open (hides itself).
  prayerApiUrl: 'https://api.aladhan.com/v1',
  prayerCity: 'Tunis',
  prayerCountry: 'Tunisia',
  prayerMethod: 18,
  // Development fixture accounts for the one-click login and the guards' silent
  // sign-in. `environment.prod.ts` declares null and angular.json swaps this file
  // in a production build, so the shipped bundle holds no known password.
  devAccounts: {
    admin: { email: 'admin@albaraka.tn', password: 'postgres' },
    client: { email: 'client@albaraka.tn', password: 'client123' }
  }
};
