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
  directly with the configured Home Assistant instance. An incomplete or
  invalid callback returns the rider to Setup with a retry message.
- The legacy GitHub Pages HTTPS callback stays published so older app releases
  that use it can still complete sign-in.

## Boundaries

- This does not add a relay, proxy, hosted Home Assistant service, or a new
  authentication provider.
- Existing saved tokens remain valid; only future sign-ins use the direct
  callback.

## HTTPS onboarding and recovery

Setup requires a Home Assistant HTTPS origin with a certificate trusted by the
Karoo and valid for the entered address. A private IP is not inherently invalid,
but adding `https://` cannot make an HTTP-only endpoint support TLS. Bare hosts
and IPs default to HTTPS; explicit HTTP is rejected before sign-in opens.

Main-document TLS, certificate, network, and HTTP failures offer **Edit address**
and **Try again**. TLS guidance describes an HTTP-only endpoint as a possibility,
not a diagnosis. Certificate errors always cancel loading; there is no bypass.
Errors in unrelated subresources do not replace the complete sign-in page.
Retry replaces pending OAuth state and callback data. Edit/Back cancels the pending
attempt, while disposing the WebView after a successful callback preserves the
code for token exchange. Concurrent consumption exchanges a code once; an exchange
from a replaced attempt cannot overwrite credentials or advance onboarding.
Token exchange failures return to Setup for a deliberate new sign-in.

This change does not alter Android network security configuration or direct Wi-Fi
and Companion transport selection. WebView traffic still uses Android's default
routing; this does not establish Wi-Fi binding for the embedded login page.
Optional HTTP support is tracked solely as an investigation in
[issue #33](https://github.com/michalchecinski/karoo-hass-companion/issues/33).

Platform behavior follows Android's [WebViewClient error callbacks](https://developer.android.com/reference/android/webkit/WebViewClient):
nonrecoverable TLS errors use `onReceivedError`, while certificate errors use
`onReceivedSslError`, which must cancel rather than proceed.
