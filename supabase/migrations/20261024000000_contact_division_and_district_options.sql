-- Contact districts, division tag, and localized district labels.
-- Existing contact/news/assignment rows and their foreign keys are preserved.
alter table public.contacts
    add column if not exists division text;

update public.districts
   set name = 'நெல்லை மாவட்டம்'
 where lower(name) = 'tirunelveli'
   and not exists (select 1 from public.districts d2 where d2.name = 'நெல்லை மாவட்டம்');

update public.districts
   set name = 'தென்காசி'
 where lower(name) = 'tenkasi'
   and not exists (select 1 from public.districts d2 where d2.name = 'தென்காசி');

insert into public.districts(name)
values ('நெல்லை மாநகரம்'), ('தூத்துக்குடி')
on conflict (name) do nothing;

notify pgrst, 'reload schema';
