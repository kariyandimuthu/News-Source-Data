-- Stock video library and private compressed news photos.
-- Storage access retains the shared-account session cutoff and device approval gates.

create table if not exists public.stock_videos (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade default auth.uid(),
    youtube_video_id text not null unique,
    youtube_url text not null,
    title text not null,
    thumbnail_url text not null,
    created_at timestamptz not null default now(),
    constraint stock_videos_video_id_format check (youtube_video_id ~ '^[A-Za-z0-9_-]{11}$')
);

alter table public.stock_videos enable row level security;
grant select, insert, update, delete on public.stock_videos to authenticated;

drop policy if exists stock_videos_shared_read on public.stock_videos;
create policy stock_videos_shared_read on public.stock_videos
    for select to authenticated using (true);

drop policy if exists stock_videos_insert_own on public.stock_videos;
create policy stock_videos_insert_own on public.stock_videos
    for insert to authenticated with check (owner_id = auth.uid());

drop policy if exists stock_videos_shared_update on public.stock_videos;
create policy stock_videos_shared_update on public.stock_videos
    for update to authenticated using (true) with check (owner_id = auth.uid());

drop policy if exists stock_videos_shared_delete on public.stock_videos;
create policy stock_videos_shared_delete on public.stock_videos
    for delete to authenticated using (true);

drop policy if exists stock_videos_approved_device_only on public.stock_videos;
create policy stock_videos_approved_device_only on public.stock_videos
    as restrictive for all to authenticated
    using (public.is_approved_app_device())
    with check (public.is_approved_app_device());

drop policy if exists stock_videos_current_session_only on public.stock_videos;
create policy stock_videos_current_session_only on public.stock_videos
    as restrictive for all to authenticated
    using (public.is_current_auth_session())
    with check (public.is_current_auth_session());

insert into storage.buckets (id, name, public, file_size_limit)
values ('news-photos', 'news-photos', false, 4194304)
on conflict (id) do update
set public = false,
    file_size_limit = least(coalesce(storage.buckets.file_size_limit, 4194304), 4194304);

-- No authenticated client receives direct Storage access. The app transfers photos
-- through the news-media Edge Function, which verifies the JWT, device secret,
-- device approval, and shared-session cutoff before using the service role.
drop policy if exists news_photos_bucket_authenticated_access on storage.objects;
drop policy if exists news_photos_approved_device_only on storage.objects;
