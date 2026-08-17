# Home Assistant icons

## Goal

Render an entity's explicitly configured Material Design Icon exactly instead of approximating it with a small domain-oriented icon set.

## Scope

- Support explicit Home Assistant entity icons whose identifiers use the `mdi:` prefix.
- Use the existing domain-oriented bundled icon when an entity has no explicit icon, the identifier is invalid, or the requested icon cannot be loaded.
- Keep custom frontend icon sets, including non-`mdi:` prefixes, outside scope.
- Keep Home Assistant frontend-derived default, device-class, and state-dependent icons outside scope when the entity does not provide an explicit icon.

## User experience

- An entity with a valid explicit `mdi:` icon uses that glyph everywhere the app currently displays its icon: Quick Access, entity discovery, and configured-action management.
- Icon loading never blocks navigation or action execution.
- When an icon cannot be rendered, the app uses its existing domain-oriented fallback without showing a broken-image placeholder.

## Functional behavior

- The planned implementation embeds a pinned complete Material Design Icons catalog as a font in the app.
- A generated lookup index maps the name after the `mdi:` prefix to the corresponding font codepoint and requires an exact catalog match.
- Icons are rendered as scalable, tintable glyphs from the bundled MDI typeface; no codepoint is exposed as user-visible text.
- The catalog is available without Wi-Fi, Companion fallback, Home Assistant, or another external service.
- The bundled catalog version is reproducible from the project source and release build.
- At implementation time, the catalog is pinned to the latest stable MDI version used by the supported Home Assistant frontend.
- The pinned version, authoritative source location, and source checksum are recorded in the project.
- Catalog updates are explicit app changes and never occur automatically during a normal build.
- A clean build downloads the pinned official MDI package as a build-time dependency, verifies its checksum, extracts the font, and generates the name-to-codepoint index.
- The downloaded font and generated index are build artifacts and are not committed to the repository.
- The build cache may reuse a previously verified artifact. A clean uncached build therefore requires network access, while the installed app never does.
- A scheduled GitHub workflow checks the MDI version used by the supported Home Assistant frontend and may open a version-update pull request.
- The update workflow never pushes directly to `main` and never automatically merges its pull request.
- Missing catalog entries and malformed identifiers use the existing domain-oriented fallback.
- Entity discovery continues to store refreshed Home Assistant icon identifiers with configured actions.
- A changed icon identifier takes effect after a successful entity refresh without changing the configured action identity.
- The Material Design Icons license and catalog version are recorded with the bundled artifact.

### Considered alternatives

**Embed packed SVG path data.** This would use native vector paths rather than font glyphs and integrate directly with path-based Compose rendering. It requires custom indexed path storage, lookup, and parsing. In the packaging estimate it added approximately 0.80 MB, compared with approximately 0.64 MB for the font and its complete unoptimized name map.

**Download individual icons from a public MDI CDN.** This would keep the APK close to its current size and transfer only a small amount of data for configured icons. It would require persistent caching, safe parsing of remote SVG content, fallback behavior before the first successful download, and a third-party runtime dependency. A pinned CDN version would still require an app release to support newer icon names, while an unpinned version would make rendering non-reproducible. Requests would also disclose the requested icon names and client network address to the CDN.

**Download icons from the user's Home Assistant frontend.** The Home Assistant frontend loads content-hashed MDI chunks using metadata compiled into the matching frontend build. Home Assistant does not document a stable per-icon REST endpoint for external clients. Depending on those internal chunk names or extracting frontend build metadata would be brittle across Home Assistant upgrades.

The current complete MDI catalog contains 7,447 icons. Adding the MDI font and its complete unoptimized name map to a copy of the current release APK increased the measured size from 8.70 MB to 9.35 MB. The expected production increase is approximately 0.65–0.8 MB; the final release size must be measured from the implemented build.

References:

- [Home Assistant frontend icon loader](https://github.com/home-assistant/frontend/blob/dev/src/components/ha-icon.ts)
- [Home Assistant REST API](https://developers.home-assistant.io/docs/api/rest/)
- [Material Design Icons](https://github.com/Templarian/MaterialDesign)
- [Material Design Icons JavaScript distribution](https://github.com/Templarian/MaterialDesign-JS)
- [Material Design Icons webfont distribution](https://github.com/Templarian/MaterialDesign-Webfont)

## Security behavior

- Rendering configured MDI icons requires no runtime request to a third-party service.
- Icon identifiers are data only and cannot introduce executable content or external resource references.
- Invalid or unsupported identifiers do not cause network access and use the bundled fallback.
- The build fails instead of using an unverified font when the downloaded artifact does not match the pinned checksum.

## Acceptance criteria

- Every valid `mdi:` identifier present in the pinned catalog resolves to its matching glyph.
- The generated name index resolves each supported icon to the correct codepoint in the bundled font.
- The same configured glyph is rendered in Quick Access, entity discovery, and configured-action management.
- Icon rendering works after a fresh app start with all network connectivity unavailable.
- An absent icon, malformed identifier, unsupported prefix, or name missing from the catalog renders the existing domain fallback.
- Changing an entity's explicit icon in Home Assistant and completing a successful entity refresh updates all app surfaces for that configured action.
- Icon loading and fallback never prevent an action from being invoked.
- Tests cover valid lookups, exact-name matching, malformed identifiers, unsupported prefixes, missing catalog names, fallback selection, and refreshed icon identifiers.
- Compose tests at the Karoo viewport verify representative simple and complex icons at the intended size and tint in every app surface.
- The release build records the bundled catalog version and required license notice.
- The catalog source checksum is verified when regenerating or replacing the bundled font and name index.
- Repeating generation from the same pinned source produces the same name-to-codepoint mapping.
- The repository does not track the downloaded font or generated name index.
- A clean build downloads and verifies the pinned package; a checksum mismatch fails the build.
- After the verified artifact is cached, the same revision can be rebuilt without downloading it again.
- The scheduled update workflow opens a pull request when the supported Home Assistant frontend adopts a newer MDI version and makes no direct change to `main`.
- The update pull request runs icon lookup tests and reports the resulting release APK size before it can be merged.
- The implemented release APK size and increase over the same build without the catalog are reported.
