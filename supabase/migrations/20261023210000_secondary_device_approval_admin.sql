-- Add a second explicitly delegated device administrator without changing existing data.
-- Original owner retains authority; ordinary approved devices cannot approve others.
create table if not exists public.app_device_admins (
    device_id uuid primary key references public.app_devices(device_id) on delete cascade,
    granted_by_device_id uuid not null references public.app_devices(device_id),
    granted_at timestamptz not null default pg_catalog.now()
);

alter table public.app_device_admins enable row level security;
revoke all on table public.app_device_admins from public, anon, authenticated;

create or replace function public.is_authorized_device_admin(
    p_device_id uuid,
    p_device_secret text
)
returns boolean
language sql
stable
security definer
set search_path = public, extensions, pg_temp
as $function$
    select
      p_device_id is not null
      and length(coalesce(p_device_secret, '')) >= 64
      and exists (
        select 1
        from public.app_devices d
        where d.device_id = p_device_id
          and d.auth_user_id = auth.uid()
          and d.status in ('owner', 'approved')
          and d.secret_hash = pg_catalog.encode(extensions.digest(p_device_secret, 'sha256'), 'hex')
          and (
            d.status = 'owner'
            or exists (
              select 1 from public.app_device_admins a
              where a.device_id = d.device_id
            )
          )
      );
$function$;

create or replace function public.require_authorized_device_admin(
    p_device_id uuid,
    p_device_secret text
)
returns void
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $function$
begin
    if auth.uid() is null or not public.is_app_admin() then
        raise exception 'Only the account owner may manage device administrators.';
    end if;
    if not public.is_authorized_device_admin(p_device_id, p_device_secret) then
        raise exception 'Device administrator verification failed.';
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
    perform public.require_authorized_device_admin(p_owner_device_id, p_owner_secret);
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
    perform public.require_authorized_device_admin(p_owner_device_id, p_owner_secret);
    if p_target_device_id is null or p_target_device_id = p_owner_device_id then
        raise exception 'Select a different device.';
    end if;

    v_new_status := case when p_approve then 'approved' else 'rejected' end;
    update public.app_devices
       set status = v_new_status,
           updated_at = pg_catalog.now()
     where device_id = p_target_device_id
       and auth_user_id = auth.uid()
       and status in ('pending', 'rejected')
       and p_target_device_id <> p_owner_device_id;

    if not found then
        raise exception 'The device request is no longer pending.';
    end if;

    return pg_catalog.jsonb_build_object('device_id', p_target_device_id, 'status', v_new_status);
end;
$function$;

create or replace function public.grant_app_device_admin(
    p_owner_device_id uuid,
    p_owner_secret text,
    p_target_device_id uuid,
    p_grant boolean
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $function$
begin
    -- Only the original owner device can delegate/remove this privilege.
    if auth.uid() is null or not public.is_app_admin()
       or p_owner_device_id is null
       or length(coalesce(p_owner_secret, '')) < 64
       or not exists (
          select 1 from public.app_devices d
          where d.device_id = p_owner_device_id
            and d.auth_user_id = auth.uid()
            and d.status = 'owner'
            and d.secret_hash = pg_catalog.encode(extensions.digest(p_owner_secret, 'sha256'), 'hex')
       ) then
        raise exception 'Only the original owner device can delegate administrator access.';
    end if;

    if p_target_device_id is null or p_target_device_id = p_owner_device_id then
        raise exception 'Choose a different device.';
    end if;

    if p_grant then
        if not exists (
          select 1 from public.app_devices d
          where d.device_id = p_target_device_id
            and d.auth_user_id = auth.uid()
            and d.status = 'approved'
        ) then
          raise exception 'Only an already approved device can become a device administrator.';
        end if;
        insert into public.app_device_admins(device_id, granted_by_device_id)
        values (p_target_device_id, p_owner_device_id)
        on conflict (device_id) do update
          set granted_by_device_id = excluded.granted_by_device_id,
              granted_at = pg_catalog.now();
    else
        delete from public.app_device_admins
         where device_id = p_target_device_id
           and granted_by_device_id = p_owner_device_id;
    end if;

    return pg_catalog.jsonb_build_object(
      'device_id', p_target_device_id,
      'is_admin', p_grant
    );
end;
$function$;

revoke all on function public.is_authorized_device_admin(uuid, text) from public, anon;
grant execute on function public.is_authorized_device_admin(uuid, text) to authenticated;
revoke all on function public.require_authorized_device_admin(uuid, text) from public, anon;
grant execute on function public.require_authorized_device_admin(uuid, text) to authenticated;
revoke all on function public.grant_app_device_admin(uuid, text, uuid, boolean) from public, anon;
grant execute on function public.grant_app_device_admin(uuid, text, uuid, boolean) to authenticated;
revoke all on function public.list_pending_app_devices(uuid, text) from public, anon;
grant execute on function public.list_pending_app_devices(uuid, text) to authenticated;
revoke all on function public.set_app_device_approval(uuid, text, uuid, boolean) from public, anon;
grant execute on function public.set_app_device_approval(uuid, text, uuid, boolean) to authenticated;

-- No existing news, assignment, contact, profile, or info data is modified.
