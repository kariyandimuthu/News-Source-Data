-- Separate, server-verified Owner password and device recovery.
-- No existing news, assignment, contacts, profile or info records are deleted or rewritten.
create extension if not exists pgcrypto with schema extensions;

create table if not exists public.app_owner_credentials (
    owner_user_id uuid primary key references auth.users(id) on delete cascade,
    password_hash text not null,
    created_by_device_id uuid not null references public.app_devices(device_id),
    created_at timestamptz not null default pg_catalog.now(),
    updated_at timestamptz not null default pg_catalog.now()
);
alter table public.app_owner_credentials enable row level security;
revoke all on table public.app_owner_credentials from public, anon, authenticated;

create table if not exists public.app_owner_password_attempts (
    auth_user_id uuid primary key references auth.users(id) on delete cascade,
    failed_attempts integer not null default 0 check (failed_attempts >= 0),
    locked_until timestamptz,
    updated_at timestamptz not null default pg_catalog.now()
);
alter table public.app_owner_password_attempts enable row level security;
revoke all on table public.app_owner_password_attempts from public, anon, authenticated;

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
       and not exists (select 1 from public.app_devices d) then
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


create or replace function public.set_app_owner_password(
    p_device_id uuid,
    p_device_secret text,
    p_new_password text
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $function$
begin
    if auth.uid() is null or not public.is_app_admin() or not public.is_approved_member() then
        raise exception 'Only an approved app owner may configure the Owner password.';
    end if;
    perform public.require_authorized_device_admin(p_device_id, p_device_secret);
    if p_new_password is null or length(p_new_password) < 10 or length(p_new_password) > 128 then
        raise exception 'Owner password must contain between 10 and 128 characters.';
    end if;

    insert into public.app_owner_credentials (
        owner_user_id, password_hash, created_by_device_id, created_at, updated_at
    ) values (
        auth.uid(),
        extensions.crypt(p_new_password, extensions.gen_salt('bf', 12)),
        p_device_id,
        pg_catalog.now(),
        pg_catalog.now()
    )
    on conflict (owner_user_id) do update
       set password_hash = excluded.password_hash,
           created_by_device_id = excluded.created_by_device_id,
           updated_at = pg_catalog.now();

    -- Transfer the single primary Owner designation to the authenticated, verified device.
    update public.app_devices
       set status = 'approved', updated_at = pg_catalog.now()
     where auth_user_id = auth.uid()
       and status = 'owner'
       and device_id <> p_device_id;

    update public.app_devices
       set status = 'owner',
           display_name = 'Muthu Rajan',
           updated_at = pg_catalog.now(),
           last_seen_at = pg_catalog.now()
     where device_id = p_device_id
       and auth_user_id = auth.uid()
       and status in ('owner', 'approved');

    if not found then
        raise exception 'The verified device cannot be promoted to Owner.';
    end if;

    delete from public.app_owner_password_attempts where auth_user_id = auth.uid();
    return pg_catalog.jsonb_build_object('success', true, 'status', 'owner');
end;
$function$;

create or replace function public.restore_app_owner_access(
    p_device_id uuid,
    p_device_secret text,
    p_device_name text,
    p_owner_password text
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $function$
declare
    v_password_hash text;
    v_failed_attempts integer;
    v_locked_until timestamptz;
begin
    if auth.uid() is null or not public.is_app_admin() or not public.is_approved_member() then
        raise exception 'Only the approved app owner can restore Owner access.';
    end if;
    if p_device_id is null or length(coalesce(p_device_secret, '')) < 64 then
        raise exception 'Device identity verification failed.';
    end if;
    if lower(pg_catalog.btrim(coalesce(p_device_name, ''))) <> 'muthu rajan' then
        raise exception 'Enter the Owner name Muthu Rajan to restore Owner access.';
    end if;
    if p_owner_password is null or length(p_owner_password) = 0 or length(p_owner_password) > 128 then
        raise exception 'Enter the separate Owner password.';
    end if;

    perform 1
      from public.app_devices d
     where d.device_id = p_device_id
       and d.auth_user_id = auth.uid()
       and d.status in ('owner', 'pending', 'approved')
       and lower(pg_catalog.btrim(d.display_name)) = 'muthu rajan'
       and d.secret_hash = pg_catalog.encode(extensions.digest(p_device_secret, 'sha256'), 'hex')
     for update;
    if not found then
        raise exception 'This device is not eligible for Owner recovery.';
    end if;

    insert into public.app_owner_password_attempts(auth_user_id)
    values (auth.uid())
    on conflict (auth_user_id) do nothing;

    select a.failed_attempts, a.locked_until
      into v_failed_attempts, v_locked_until
      from public.app_owner_password_attempts a
     where a.auth_user_id = auth.uid()
     for update;

    if v_locked_until is not null and v_locked_until > pg_catalog.now() then
        return pg_catalog.jsonb_build_object(
            'success', false,
            'message', 'பல தவறான முயற்சிகள் பதிவாகியுள்ளன. 15 நிமிடங்கள் கழித்து முயற்சிக்கவும்.'
        );
    end if;

    select c.password_hash
      into v_password_hash
      from public.app_owner_credentials c
     where c.owner_user_id = auth.uid();

    if v_password_hash is null then
        return pg_catalog.jsonb_build_object(
            'success', false,
            'message', 'Owner கடவுச்சொல் இன்னும் உருவாக்கப்படவில்லை. Owner அமைப்புகளை ஏற்கெனவே அணுகக்கூடிய சாதனத்தில் முதலில் உருவாக்கவும்.'
        );
    end if;

    if extensions.crypt(p_owner_password, v_password_hash) is distinct from v_password_hash then
        v_failed_attempts := case
            when v_locked_until is not null and v_locked_until <= pg_catalog.now() then 0
            else coalesce(v_failed_attempts, 0)
        end + 1;

        update public.app_owner_password_attempts
           set failed_attempts = v_failed_attempts,
               locked_until = case
                   when v_failed_attempts >= 5 then pg_catalog.now() + interval '15 minutes'
                   else null
               end,
               updated_at = pg_catalog.now()
         where auth_user_id = auth.uid();

        return pg_catalog.jsonb_build_object(
            'success', false,
            'message', case
                when v_failed_attempts >= 5 then 'Owner கடவுச்சொல் தவறானது. 15 நிமிடங்களுக்கு முயற்சி நிறுத்தப்பட்டுள்ளது.'
                else 'Owner கடவுச்சொல் தவறானது. மீண்டும் சரிபார்க்கவும்.'
            end
        );
    end if;

    update public.app_owner_password_attempts
       set failed_attempts = 0, locked_until = null, updated_at = pg_catalog.now()
     where auth_user_id = auth.uid();

    -- Correct Owner secret transfers the single primary Owner designation to this device.
    update public.app_devices
       set status = 'approved', updated_at = pg_catalog.now()
     where auth_user_id = auth.uid()
       and status = 'owner'
       and device_id <> p_device_id;

    update public.app_devices
       set status = 'owner',
           display_name = 'Muthu Rajan',
           updated_at = pg_catalog.now(),
           last_seen_at = pg_catalog.now()
     where device_id = p_device_id
       and auth_user_id = auth.uid();

    if not found then
        raise exception 'Owner recovery failed while updating this device.';
    end if;

    return pg_catalog.jsonb_build_object(
        'success', true,
        'status', 'owner',
        'message', 'Owner அணுகல் மீட்டெடுக்கப்பட்டது.'
    );
end;
$function$;

revoke all on function public.set_app_owner_password(uuid, text, text) from public, anon;
grant execute on function public.set_app_owner_password(uuid, text, text) to authenticated;
revoke all on function public.restore_app_owner_access(uuid, text, text, text) from public, anon;
grant execute on function public.restore_app_owner_access(uuid, text, text, text) to authenticated;

-- Only bcrypt hashes are stored; direct client access to the credential and attempt tables is revoked.
