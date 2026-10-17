-- Immutable, per-account save snapshots. Disc images are never stored here.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('portal-saves', 'portal-saves', false, 20971520, array['application/zip'])
on conflict (id) do nothing;

create policy "Portal owners upload snapshots"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'portal-saves'
  and (storage.foldername(name))[1] = (select auth.uid()::text)
);

create policy "Portal owners read snapshots"
on storage.objects for select to authenticated
using (
  bucket_id = 'portal-saves'
  and (storage.foldername(name))[1] = (select auth.uid()::text)
);

create table public.portal_snapshots (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  scope text not null check (scope = 'figures' or scope ~ '^wii:[0-9A-F]{8}$'),
  parent_id uuid,
  object_key text not null unique,
  sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
  bytes bigint not null check (bytes between 1 and 20971520),
  device_id uuid not null,
  created_at timestamptz not null default now(),
  unique (user_id, id),
  foreign key (user_id, parent_id) references public.portal_snapshots(user_id, id)
);

create table public.portal_heads (
  user_id uuid not null references auth.users(id) on delete cascade,
  scope text not null check (scope = 'figures' or scope ~ '^wii:[0-9A-F]{8}$'),
  snapshot_id uuid not null,
  updated_at timestamptz not null default now(),
  primary key (user_id, scope),
  foreign key (user_id, snapshot_id) references public.portal_snapshots(user_id, id)
);

alter table public.portal_snapshots enable row level security;
alter table public.portal_heads enable row level security;

create policy "Owners read their save history"
on public.portal_snapshots for select to authenticated
using (user_id = (select auth.uid()));

create policy "Owners read their current saves"
on public.portal_heads for select to authenticated
using (user_id = (select auth.uid()));

revoke all on public.portal_snapshots from anon, authenticated;
revoke all on public.portal_heads from anon, authenticated;
grant select on public.portal_snapshots, public.portal_heads to authenticated;

-- A conditional head move prevents an old/offline device from replacing a newer save.
-- The uploaded file and snapshot row remain available if a conflict occurs.
create function public.publish_portal_snapshot(
  p_snapshot_id uuid,
  p_scope text,
  p_parent_id uuid,
  p_sha256 text,
  p_bytes bigint,
  p_device_id uuid
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_object_key text;
  v_existing public.portal_snapshots%rowtype;
  v_current uuid;
begin
  if v_user_id is null then
    raise exception 'Authentication required' using errcode = '28000';
  end if;
  if p_snapshot_id is null or p_device_id is null
     or not (p_scope = 'figures' or p_scope ~ '^wii:[0-9A-F]{8}$')
     or p_sha256 !~ '^[0-9a-f]{64}$'
     or p_bytes not between 1 and 20971520 then
    raise exception 'Invalid snapshot metadata' using errcode = '22023';
  end if;
  if p_parent_id is not null and not exists (
    select 1 from public.portal_snapshots
    where id = p_parent_id and user_id = v_user_id and scope = p_scope
  ) then
    raise exception 'Invalid parent snapshot' using errcode = '22023';
  end if;

  v_object_key := v_user_id::text || '/' || p_snapshot_id::text || '.zip';
  if not exists (
    select 1 from storage.objects
    where bucket_id = 'portal-saves' and name = v_object_key
  ) then
    raise exception 'Snapshot upload is incomplete' using errcode = '22023';
  end if;

  insert into public.portal_snapshots
    (id, user_id, scope, parent_id, object_key, sha256, bytes, device_id)
  values
    (p_snapshot_id, v_user_id, p_scope, p_parent_id, v_object_key,
     p_sha256, p_bytes, p_device_id)
  on conflict (id) do nothing;

  select * into v_existing from public.portal_snapshots where id = p_snapshot_id;
  if v_existing.user_id is distinct from v_user_id
     or v_existing.scope is distinct from p_scope
     or v_existing.parent_id is distinct from p_parent_id
     or v_existing.object_key is distinct from v_object_key
     or v_existing.sha256 is distinct from p_sha256
     or v_existing.bytes is distinct from p_bytes
     or v_existing.device_id is distinct from p_device_id then
    raise exception 'Snapshot ID was already used' using errcode = '23505';
  end if;

  insert into public.portal_heads (user_id, scope, snapshot_id)
  values (v_user_id, p_scope, p_snapshot_id)
  on conflict (user_id, scope) do update
    set snapshot_id = excluded.snapshot_id, updated_at = now()
    where public.portal_heads.snapshot_id is not distinct from p_parent_id;

  if found then
    return jsonb_build_object('status', 'current', 'snapshot_id', p_snapshot_id);
  end if;

  select snapshot_id into v_current from public.portal_heads
    where user_id = v_user_id and scope = p_scope;
  if v_current = p_snapshot_id then
    return jsonb_build_object('status', 'current', 'snapshot_id', p_snapshot_id);
  end if;
  return jsonb_build_object('status', 'conflict', 'snapshot_id', p_snapshot_id,
                            'current_id', v_current);
end;
$$;

revoke all on function public.publish_portal_snapshot(uuid, text, uuid, text, bigint, uuid)
  from public, anon;
grant execute on function public.publish_portal_snapshot(uuid, text, uuid, text, bigint, uuid)
  to authenticated;
