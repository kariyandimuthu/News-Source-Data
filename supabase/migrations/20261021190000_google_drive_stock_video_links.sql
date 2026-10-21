-- Switch the Stock Videos library to Google Drive file links.
-- Existing YouTube rows are preserved for audit/data safety but the app no longer lists or accepts them.

alter table public.stock_videos
    add column if not exists drive_file_id text,
    add column if not exists drive_url text,
    add column if not exists preview_url text,
    add column if not exists file_name text;

-- Permit new rows to omit retired YouTube fields while keeping old rows intact.
alter table public.stock_videos
    alter column youtube_video_id drop not null,
    alter column youtube_url drop not null;

create unique index if not exists stock_videos_drive_file_id_unique
    on public.stock_videos (drive_file_id)
    where drive_file_id is not null;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'stock_videos_drive_file_id_format'
          and conrelid = 'public.stock_videos'::regclass
    ) then
        alter table public.stock_videos
            add constraint stock_videos_drive_file_id_format
            check (drive_file_id is null or drive_file_id ~ '^[A-Za-z0-9_-]{10,200}$');
    end if;
end
$$;
