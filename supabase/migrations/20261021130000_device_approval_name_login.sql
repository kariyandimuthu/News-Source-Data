-- Name/password login and device-level approval for News Source Data.
-- Install the new APK on the existing Muthu Rajan device first, before other devices.
create extension if not exists pgcrypto with schema extensions;

create table if not exists public.app_devices (
    device_id uuid primary key,
    auth_user_id uuid not null references auth.users(id) on delete cascade,
    display_name text not null,
    status text not null check (status in ('owner', 'pending', 'approved', 'rejected', 'revoked')),
    secret_hash text not null,
    created_at timestamptz not null default pg_catalog.now(),
    updated_at timestamptz not null default pg_catalog.now(),
    last_seen_at timestamptz not null default pg_catalog.now()
);

create unique index if not exists app_devices_one_owner
    on public.app_devices (status) where status = 'owner';

alter table public.app_devices enable row level security;
revoke all on table public.app_devices from public, anon, authenticated;

create or replace function public.register_app_device(
    p_device_id uuid,
    p_device_name text,
    p_device_secret text
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $function$
declare
    v_status text;
    v_secret_hash text;
    v_existing_hash text;
    v_display_name text;
begin
    if auth.uid() is null then
        raise exception 'Sign in is required.';
    end if;
    if p_device_id is null or length(coalesce(p_device_secret, '')) < 64 then
        raise exception 'Invalid device identity.';
    end if;
    v_display_name := nullif(pg_catalog.btrim(coalesce(p_device_name, '')), '');
    if v_display_name is null or length(v_display_name) > 80 then
        raise exception 'Enter a valid device name.';
    end if;
    if not public.is_approved_member() then
        raise exception 'This account is not approved.';
    end if;

    v_secret_hash := pg_catalog.encode(extensions.digest(p_device_secret, 'sha256'), 'hex');

    select d.status, d.secret_hash, d.display_name
      into v_status, v_existing_hash, v_display_name
      from public.app_devices d
     where d.device_id = p_device_id and d.auth_user_id = auth.uid()
     for update;

    if found then
        if v_existing_hash <> v_secret_hash then
            raise exception 'Device identity verification failed.';
        end if;
        if v_status = 'revoked' then
            return pg_catalog.jsonb_build_object('status', 'revoked', 'display_name', v_display_name);
        end if;

        if v_status = 'owner' then
            v_display_name := 'Muthu Rajan';
        else
            v_display_name := nullif(pg_catalog.btrim(coalesce(p_device_name, '')), '');
        end if;

        update public.app_devices
           set display_name = v_display_name,
               updated_at = pg_catalog.now(),
               last_seen_at = pg_catalog.now()
         where device_id = p_device_id;

        return pg_catalog.jsonb_build_object(
            'status', v_status,
            'display_name', v_display_name,
            'device_id', p_device_id
        );
    end if;

    if exists (select 1 from public.app_devices d where d.device_id = p_device_id) then
        raise exception 'Device identity belongs to a different account.';
    end if;

    if lower(v_display_name) = 'muthu rajan'
       and public.is_app_admin()
       and not exists (select 1 from public.app_devices d where d.status = 'owner') then
        v_status := 'owner';
        v_display_name := 'Muthu Rajan';
    else
        v_status := 'pending';
    end if;

    insert into public.app_devices (
        device_id, auth_user_id, display_name, status, secret_hash
    ) values (
        p_device_id, auth.uid(), v_display_name, v_status, v_secret_hash
    );

    return pg_catalog.jsonb_build_object(
        'status', v_status,
        'display_name', v_display_name,
        'device_id', p_device_id
    );
end;
$function$;

create or replace function public.require_owner_device(
    p_owner_device_id uuid,
    p_owner_secret text
)
returns void
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $function$
begin
    if auth.uid() is null or not public.is_app_admin() then
        raise exception 'Only the owner can approve devices.';
    end if;
    if p_owner_device_id is null
       or length(coalesce(p_owner_secret, '')) < 64
       or not exists (
            select 1
              from public.app_devices d
             where d.device_id = p_owner_device_id
               and d.auth_user_id = auth.uid()
               and d.status = 'owner'
               and d.secret_hash = pg_catalog.encode(extensions.digest(p_owner_secret, 'sha256'), 'hex')
       ) then
        raise exception 'Owner device verification failed.';
    end if;
end;
$function$;

create or replace function public.list_pending_app_devices(
    p_owner_device_id uuid,
    p_owner_secret text
)
returns table(device_id uuid, display_name text, status text, created_at timestamptz)
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $function$
begin
    perform public.require_owner_device(p_owner_device_id, p_owner_secret);
    return query
        select d.device_id, d.display_name, d.status, d.created_at
          from public.app_devices d
         where d.auth_user_id = auth.uid()
           and d.status in ('pending', 'rejected')
         order by d.created_at desc;
end;
$function$;

create or replace function public.set_app_device_approval(
    p_owner_device_id uuid,
    p_owner_secret text,
    p_target_device_id uuid,
    p_approve boolean
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $function$
declare
    v_new_status text;
begin
    perform public.require_owner_device(p_owner_device_id, p_owner_secret);
    if p_target_device_id is null or p_target_device_id = p_owner_device_id then
        raise exception 'Select a different device.';
    end if;

    v_new_status := case when p_approve then 'approved' else 'rejected' end;
    update public.app_devices
       set status = v_new_status,
           updated_at = pg_catalog.now()
     where device_id = p_target_device_id
       and auth_user_id = auth.uid()
       and status in ('pending', 'rejected');

    if not found then
        raise exception 'The device request is no longer pending.';
    end if;

    return pg_catalog.jsonb_build_object('device_id', p_target_device_id, 'status', v_new_status);
end;
$function$;

create or replace function public.is_approved_app_device()
returns boolean
language plpgsql
stable
security definer
set search_path = public, extensions, pg_temp
as $function$
declare
    v_headers jsonb;
    v_device_id text;
    v_device_secret text;
begin
    if auth.uid() is null then
        return false;
    end if;

    v_headers := coalesce(
        nullif(pg_catalog.current_setting('request.headers', true), '')::jsonb,
        '{}'::jsonb
    );
    v_device_id := v_headers ->> 'x-app-device-id';
    v_device_secret := v_headers ->> 'x-app-device-secret';

    if v_device_id is null or v_device_secret is null or length(v_device_secret) < 64 then
        return false;
    end if;

    return exists (
        select 1
          from public.app_devices d
         where d.device_id::text = v_device_id
           and d.auth_user_id = auth.uid()
           and d.status in ('owner', 'approved')
           and d.secret_hash = pg_catalog.encode(extensions.digest(v_device_secret, 'sha256'), 'hex')
    );
exception when others then
    return false;
end;
$function$;

revoke all on function public.register_app_device(uuid, text, text) from public, anon;
grant execute on function public.register_app_device(uuid, text, text) to authenticated;
revoke all on function public.require_owner_device(uuid, text) from public, anon;
grant execute on function public.require_owner_device(uuid, text) to authenticated;
revoke all on function public.list_pending_app_devices(uuid, text) from public, anon;
grant execute on function public.list_pending_app_devices(uuid, text) to authenticated;
revoke all on function public.set_app_device_approval(uuid, text, uuid, boolean) from public, anon;
grant execute on function public.set_app_device_approval(uuid, text, uuid, boolean) to authenticated;
revoke all on function public.is_approved_app_device() from public, anon;
grant execute on function public.is_approved_app_device() to authenticated;

drop policy if exists approved_device_only on public.profiles;
create policy approved_device_only on public.profiles
    as restrictive for all to authenticated
    using (public.is_approved_app_device())
    with check (public.is_approved_app_device());

drop policy if exists approved_device_only on public.districts;
create policy approved_device_only on public.districts
    as restrictive for all to authenticated
    using (public.is_approved_app_device())
    with check (public.is_approved_app_device());

drop policy if exists approved_device_only on public.police_stations;
create policy approved_device_only on public.police_stations
    as restrictive for all to authenticated
    using (public.is_approved_app_device())
    with check (public.is_approved_app_device());

drop policy if exists approved_device_only on public.news_items;
create policy approved_device_only on public.news_items
    as restrictive for all to authenticated
    using (public.is_approved_app_device())
    with check (public.is_approved_app_device());

drop policy if exists approved_device_only on public.assignments;
create policy approved_device_only on public.assignments
    as restrictive for all to authenticated
    using (public.is_approved_app_device())
    with check (public.is_approved_app_device());

drop policy if exists approved_device_only on public.contacts;
create policy approved_device_only on public.contacts
    as restrictive for all to authenticated
    using (public.is_approved_app_device())
    with check (public.is_approved_app_device());

drop policy if exists approved_device_only on public.calendar_events;
create policy approved_device_only on public.calendar_events
    as restrictive for all to authenticated
    using (public.is_approved_app_device())
    with check (public.is_approved_app_device());

drop policy if exists approved_device_only on public.news_categories;
create policy approved_device_only on public.news_categories
    as restrictive for all to authenticated
    using (public.is_approved_app_device())
    with check (public.is_approved_app_device());
