# Owner application API boundary

This API contract implements the approved decisions in `owner-onboarding-boundaries.md`. Android sends the Firebase ID token as `Authorization: Bearer <token>` to Supabase Edge Functions. The function independently verifies the token, derives UID from `sub`, reads current Firestore state using the service account, and never trusts client `role`, `ownerStatus`, `status`, `rejectReason`, or review metadata.

All functions accept `POST` JSON and return JSON. Errors use the shared shape `{ "error": { "code": "...", "message": "..." } }`. Supabase gateway JWT verification remains disabled for these functions because Firebase tokens are verified by shared `_shared/auth.ts`; function handlers must call the verifier themselves. No privileged Supabase/Firebase secret is shipped in the app.

## `owner-applications`

### Read current state

Request:

```json
{ "action": "STATUS" }
```

Response is based on authoritative `users/{uid}.ownerStatus` and, when applicable, the latest application for that UID ordered by `submittedAt DESC`:

```json
{
  "ownerStatus": "NONE | PENDING | APPROVED | REJECTED",
  "role": "PLAYER | OWNER",
  "application": {
    "applicationId": "...",
    "venueDraft": {},
    "representativeName": "...",
    "representativePhone": "...",
    "documentPaths": [],
    "status": "PENDING | APPROVED | REJECTED",
    "rejectReason": null,
    "submittedAt": "ISO-8601"
  }
}
```

`application` is null when no application exists. The handler rejects missing user records and inconsistent user/application states rather than infer a role from client state.

### Submit or resubmit

Request:

```json
{
  "action": "SUBMIT",
  "applicationId": "client-generated UUID retained for this lifecycle",
  "venueDraft": {
    "name": "...", "address": "...", "districtId": "...",
    "location": { "latitude": 10.77, "longitude": 106.66 },
    "phone": "...", "courtCount": 5, "openTime": "0530", "closeTime": "2230",
    "photoPaths": ["uid/applicationId/filename.webp"]
  },
  "representativeName": "...",
  "representativePhone": "...",
  "documentPaths": ["uid/applicationId/filename.jpg"]
}
```

The function validates all applicant-owned fields and verifies every object path starts with the authenticated UID and supplied application ID. `NONE` requires the application document not to exist, then atomically creates `ownerApplications/{applicationId}` and changes `users.ownerStatus` to `PENDING`; `users.role` remains unchanged. `REJECTED` requires the same existing application ID/document owned by the UID, then atomically updates applicant fields, sets application status `PENDING`, refreshes `submittedAt`, clears `rejectReason`, and sets user status `PENDING`. `PENDING` and `APPROVED` reject submission. A retry of the exact committed payload/application ID returns the already-committed current state. A different payload or lifecycle race returns HTTP 409 `APPLICATION_CONFLICT`; callers refresh status and retain their draft.

Application IDs are generated once per new lifecycle and persisted with the local draft. Rejected resubmission reuses its document ID. After owner revocation, a new lifecycle receives a new ID. The server verifies ID format and lifecycle preconditions; document identity is not an authorization credential.

## `owner-docs` action `SIGN_UPLOAD`

Request:

```json
{
  "action": "SIGN_UPLOAD",
  "applicationId": "...",
  "fileName": "business-license.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 12345
}
```

The server permits only JPEG/PNG/WebP and sizes from 1 through 2,097,152 bytes. For `ownerStatus=NONE`, no application with this ID may already exist. For `REJECTED`, the ID must identify the applicant's current rejected document. It generates an opaque object name under `{uid}/{applicationId}/` and returns `{ "path": "...", "signedUrl": "...", "token": "..." }`. The signed upload is single-use/non-upsert. The app stores only `path`; an uploaded file is considered attached only after upload succeeds.

Private `owner-docs` staging contains both evidence and venue photos before review. Feature 120 promotes venue photos to `venue-photos` during approval. The Edge Function does not allow uploads for `PENDING` or `APPROVED` applications.

## `owner-docs` action `SIGN_DOWNLOAD`

Request:

```json
{ "applicationId": "...", "path": "uid/applicationId/file.jpg" }
```

The server loads the application and verifies that the requested path is in `documentPaths`. It returns a private signed URL with at most 300 seconds lifetime to the applicant or a caller whose verified Firebase `appRole` is `ADMIN`. It never signs venue photo paths or arbitrary object paths. On URL expiry, the app requests a fresh URL.

## Cleanup boundary

The app may request deletion only for its own staged path after cancellation/replacement, through a UID-scoped cleanup function. A server sweeper removes unreferenced staging objects only after 30 days. Account deletion remains owned by Feature 011 and removes the UID's applications and `owner-docs/{uid}/` prefix. Evidence remains private and is not deleted on approval/rejection.

## Approval integration

Feature 120 remains the only reviewer and only owner-role grantor. Its approval path promotes venue photos first, then atomically creates `venues/{applicationId}` and updates application/user Firestore documents, then updates the Firebase custom claim and sends the Feature 100 notification. Each post-transaction operation must be retry-safe. Feature 110 does not call approval or create a venue. Feature 111 only opens after a server refresh confirms `APPROVED`/`OWNER`.

## Storage configuration

`owner-docs` is private with allowed MIME types JPEG/PNG/WebP and 2 MiB object limit. Download signed URLs expire after five minutes. Upload tokens are single-use/non-upsert and their lifetime follows the configured Supabase Storage signed-upload behavior. No new Firestore collection, field, enum, index, or Android dependency is introduced by this boundary. Bucket object paths and all request fields are validated server-side.
