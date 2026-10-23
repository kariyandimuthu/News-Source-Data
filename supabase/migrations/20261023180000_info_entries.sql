-- Info dictionary/statistics table for News Source Data.
-- Uses the same shared-session and approved-device gates as other app data.
create table if not exists public.info_entries (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    label text not null check (btrim(label) <> ''),
    value text not null default '',
    group_name text not null default '',
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists info_entries_sort_label_idx
    on public.info_entries (sort_order asc, label asc);

alter table public.info_entries enable row level security;
grant select, insert, update, delete on public.info_entries to authenticated;

drop policy if exists info_entries_device_gate on public.info_entries;
create policy info_entries_device_gate on public.info_entries
    as restrictive for all to authenticated
    using (public.is_approved_app_device())
    with check (public.is_approved_app_device());

drop policy if exists info_entries_session_gate on public.info_entries;
create policy info_entries_session_gate on public.info_entries
    as restrictive for all to authenticated
    using (public.is_current_auth_session())
    with check (public.is_current_auth_session());

drop policy if exists info_entries_select_approved_shared on public.info_entries;
create policy info_entries_select_approved_shared on public.info_entries
    for select to authenticated
    using (public.is_approved_member());

drop policy if exists info_entries_insert_approved_own on public.info_entries;
create policy info_entries_insert_approved_own on public.info_entries
    for insert to authenticated
    with check (public.is_approved_member() and owner_id = auth.uid());

drop policy if exists info_entries_update_approved_own on public.info_entries;
create policy info_entries_update_approved_own on public.info_entries
    for update to authenticated
    using (public.is_approved_member() and owner_id = auth.uid())
    with check (public.is_approved_member() and owner_id = auth.uid());

drop policy if exists info_entries_delete_approved_own on public.info_entries;
create policy info_entries_delete_approved_own on public.info_entries
    for delete to authenticated
    using (public.is_approved_member() and owner_id = auth.uid());
