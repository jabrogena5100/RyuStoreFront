# Auth API contract (slice 3b)

This is the contract the designed screens (sign in, create account, account menu) are built against.
It is written so it can be pasted into Claude Design as the list of states each screen must cover.
Everything here is **implemented but not yet verified** until the rows in `docs/verification.md` say so.

Same-origin only: the Next.js app and the API are served from one domain (`/api/*` on the ALB), so there
is no CORS. The session cookie is `SESSION` (HttpOnly, SameSite=Lax, Secure when deployed, 30-minute
idle timeout). JavaScript never sees it.

## Calling convention

1. Call `GET /api/auth/csrf` once on page load. It returns `{"headerName":"X-CSRF-TOKEN","token":"..."}`.
2. Send that token in the named header on **every** POST, including register and login.
3. `register` and `login` return a **new** token (`csrf`) because the session id and token rotate on
   sign-in. Replace the stored one. After `logout`, call `GET /csrf` again.
4. A 403 with `code: "csrf_invalid"` means: fetch a new token and retry once, then show the generic error.

## Endpoints

| Method and path | Body | Success | Errors |
| --- | --- | --- | --- |
| `GET /api/auth/csrf` | none | 200 `{headerName, token}` | none |
| `POST /api/auth/register` | `{email, password, displayName?}` | 201 `{customer, csrf}` and signed in | 400 `validation_failed`, 409 `email_taken`, 403 `csrf_invalid` |
| `POST /api/auth/login` | `{email, password}` | 200 `{customer, csrf}` | 400 `validation_failed`, 401 `invalid_credentials`, 403 `csrf_invalid` |
| `POST /api/auth/logout` | none | 204 | 403 `csrf_invalid` |
| `GET /api/auth/me` | none | 200 `{id, email, displayName, role}` | 401 `unauthenticated` |

`customer` is `{id, email, displayName, role}`; `role` is `customer` or `admin`. `displayName` may be null.

Every error body has the same shape, so one error component serves every form:

```json
{ "code": "validation_failed", "message": "Check the highlighted fields.", "fields": { "password": "length" } }
```

`code` and the `fields` values are stable identifiers; the wording shown to people belongs to the design.
`message` is English fallback text and may change.

## Field rules and codes

| Field | Rule | Codes |
| --- | --- | --- |
| `email` | required, at most 100 characters, printable ASCII, valid format. Trimmed and lowercased by the server | `required`, `length`, `invalid_email`, `taken` (register only) |
| `password` (register) | required, 10 to 72 characters and at most 72 bytes | `required`, `length`, `too_long` (72-byte limit hit by multi-byte characters) |
| `displayName` | optional, at most 80 characters | `length` |

Login never says which half was wrong: unknown email and wrong password return the identical 401
`invalid_credentials`. The design should show one message for both, not "no such account".

## States each screen needs

| Screen | States to design |
| --- | --- |
| Create account | idle, submitting (button disabled and busy), field errors for each code above, `email_taken` (offer "Sign in instead"), generic error (network, 500), success (lands signed in; go to where the person came from) |
| Sign in | idle, submitting, `invalid_credentials` (form-level error, not under a field), generic error, success |
| Account menu / header | guest (Sign in, Create account), signed in (name or email, Orders, Sign out), loading while `/me` resolves (avoid a flash of the guest state) |
| Session expired | any call returning 401 `unauthenticated` while the UI believes it is signed in: return to Sign in and keep the page the person was on |
| Admin | a customer who reaches an admin page gets 403 `forbidden`: show a not-allowed state, not a blank page |

## Known limits (recorded, not hidden)

- `email_taken` reveals that an address has an account. Accepted for v1; the alternative (email
  verification flow) is out of scope.
- No rate limiting or lockout yet (phase 6). No password reset or email verification (out of v1 scope).
- Anyone can call `GET /csrf`, which creates a session row. Rate limiting in phase 6 covers it.
- A role change in the database takes effect at the person's next sign-in, not mid-session.
