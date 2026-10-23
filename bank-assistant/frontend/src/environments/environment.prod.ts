export const environment = {
  production: true,
  apiUrl: '/api',
  // Prayer times — Aladhan API, official Tunisia method (18), default city
  // Tunis. See docs/UIUX_RESEARCH.md §5. The widget fails open (hides itself).
  prayerApiUrl: 'https://api.aladhan.com/v1',
  prayerCity: 'Tunis',
  prayerCountry: 'Tunisia',
  prayerMethod: 18,
  // No fixture account exists in a production build: there is nothing to sign
  // in with, and nothing to leak.
  devAccounts: null
};
