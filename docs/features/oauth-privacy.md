# Direct OAuth callback and privacy notice

## Goal

Make the Home Assistant sign-in route understandable before a rider leaves the
app to authenticate, and keep new authorization results out of the GitHub Pages
callback route.

## Behavior

- The setup screen explains that Home Assistant traffic goes directly between
  the Karoo and the configured Home Assistant address, and that the webpage
  used during sign-in identifies the app without proxying traffic.
- GitHub Pages remains the public OAuth client identity required by Home
  Assistant, but its client metadata approves the app's
  `karoohass://auth-callback` redirect URI.
- After approval, Home Assistant opens that deep link directly. The app checks
  the callback URI, validates OAuth state, and exchanges the authorization code
  directly with the configured Home Assistant instance. An incomplete, stale,
  or invalid callback returns the rider to Setup with a retry message.
- The legacy GitHub Pages HTTPS callback stays published so older app releases
  that use it can still complete sign-in.

## Boundaries

- This does not add a relay, proxy, hosted Home Assistant service, or a new
  authentication provider.
- Existing saved tokens remain valid; only future sign-ins use the direct
  callback.
