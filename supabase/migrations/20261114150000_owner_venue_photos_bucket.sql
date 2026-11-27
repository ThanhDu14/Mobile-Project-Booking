-- Photos are public; writes require short-lived signed upload tickets issued after ownership checks.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('venue-photos', 'venue-photos', true, 2097152, array['image/jpeg','image/png','image/webp'])
on conflict (id) do nothing;
