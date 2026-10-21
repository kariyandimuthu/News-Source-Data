-- Shared-account session cut-off for News Source Data.
-- Revoked access JWTs may remain cryptographically valid until exp; this RLS gate
-- blocks those older JWTs from reading/writing application tables immediately.

create table if not exists public.app_security_control (
    singleton boolean primary key default true check (singleton),
    sessions_valid_after timestamptz not null default pg_catalog.to_timestamp(0)
);

insert into public.app_security_control (singleton, sessions_valid_after)
values (true, pg_catalog.to_timestamp(0))
on conflict (singleton) do nothing;

alter table public.app_security_control enable row level security;
revoke all on table public.app_security_control from public, anon, authenticated;

create or replace function public.is_current_auth_session()
returns boolean
language sql
stable
security definer
set search_path = public
as $function$
    select case
        when auth.uid() is null then false
        when coalesce(auth.jwt() ->> 'iat', '') ~ '^[0-9]+$'
            then pg_catalog.to_timestamp((auth.jwt() ->> 'iat')::double precision) >= c.sessions_valid_after
        else false
    end
    from public.app_security_control c
    where c.singleton = true;
$function$;

create or replace function public.revoke_all_shared_sessions()
returns void
language plpgsql
security definer
set search_path = public
as $function$
begin
    if not public.is_app_admin() then
        raise exception 'Only the app owner can revoke all shared sessions.';
    end if;

    update public.app_security_control
    set sessions_valid_after = pg_catalog.to_timestamp(
        floor(extract(epoch from pg_catalog.clock_timestamp())) + 1
    )
    where singleton = true;

    if not found then
        raise exception 'Session security control row is missing.';
    end if;
end;
$function$;

revoke all on function public.is_current_auth_session() from public, anon;
grant execute on function public.is_current_auth_session() to authenticated;
revoke all on function public.revoke_all_shared_sessions() from public, anon;
grant execute on function public.revoke_all_shared_sessions() to authenticated;

drop policy if exists shared_session_cutoff on public.profiles;
create policy shared_session_cutoff on public.profiles
as restrictive for all to authenticated
using (public.is_current_auth_session())
with check (public.is_current_auth_session());

drop policy if exists shared_session_cutoff on public.districts;
create policy shared_session_cutoff on public.districts
as restrictive for all to authenticated
using (public.is_current_auth_session())
with check (public.is_current_auth_session());

drop policy if exists shared_session_cutoff on public.police_stations;
create policy shared_session_cutoff on public.police_stations
as restrictive for all to authenticated
using (public.is_current_auth_session())
with check (public.is_current_auth_session());

drop policy if exists shared_session_cutoff on public.news_items;
create policy shared_session_cutoff on public.news_items
as restrictive for all to authenticated
using (public.is_current_auth_session())
with check (public.is_current_auth_session());

drop policy if exists shared_session_cutoff on public.assignments;
create policy shared_session_cutoff on public.assignments
as restrictive for all to authenticated
using (public.is_current_auth_session())
with check (public.is_current_auth_session());

drop policy if exists shared_session_cutoff on public.contacts;
create policy shared_session_cutoff on public.contacts
as restrictive for all to authenticated
using (public.is_current_auth_session())
with check (public.is_current_auth_session());

drop policy if exists shared_session_cutoff on public.calendar_events;
create policy shared_session_cutoff on public.calendar_events
as restrictive for all to authenticated
using (public.is_current_auth_session())
with check (public.is_current_auth_session());

drop policy if exists shared_session_cutoff on public.news_categories;
create policy shared_session_cutoff on public.news_categories
as restrictive for all to authenticated
using (public.is_current_auth_session())
with check (public.is_current_auth_session());
