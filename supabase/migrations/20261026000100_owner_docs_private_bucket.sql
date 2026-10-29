-- Local/development bootstrap for the bucket already defined by database README §8.
-- Existing bucket configuration is preserved; applicant access remains mediated by
-- the authenticated Edge Functions because Storage RLS cannot read Firestore.
insert into storage.buckets (
  id,
  name,
  public,
  file_size_limit,
  allowed_mime_types
)
values (
  'owner-docs',
  'owner-docs',
  false,
  2097152,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do nothing;

-- No storage.objects client policies are added. With RLS enabled and no matching
-- policy, direct client reads/writes are denied; signed access uses the Edge
-- Function service boundary after Firestore ownership verification.
