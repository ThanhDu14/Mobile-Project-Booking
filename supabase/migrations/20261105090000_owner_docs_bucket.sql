-- Private staging for applicant evidence and venue photos (Feature 110).
-- All access is mediated by authenticated Edge Functions and one-time signed URLs.
-- No direct authenticated/anonymous storage.objects policy is granted here.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('owner-docs', 'owner-docs', false, 2097152, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
  set public = false,
      file_size_limit = 2097152,
      allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp'];
