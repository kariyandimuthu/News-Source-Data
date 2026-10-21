# News photo Storage cleanup

This function is intentionally **not scheduled or deployed automatically** by this repository change. Configure it only after creating the photo bucket and verifying the `attachment_paths` format in the app.

## Behavior
- Checks the configured bucket's object sizes.
- Does nothing at or below 800 MiB.
- If above 800 MiB, removes oldest unprotected objects through the Supabase Storage API until the estimated total is at or below 700 MiB.
- Protects paths referenced by `news_items.attachment_paths` for items whose priority is `high`, `critical`, or `urgent`.
- Never deletes rows from `news_items`.
- Requires the private `PHOTO_CLEANUP_SECRET` header. Never expose this secret in the Android app.
- Uses `SUPABASE_SERVICE_ROLE_KEY` only as an Edge Function secret. Never ship it in the APK.

## Required Edge Function secrets
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `PHOTO_CLEANUP_SECRET` (long random value)
- `NEWS_PHOTO_BUCKET` (the exact bucket ID)

## Before scheduling
1. Create the private photo bucket and enforce Storage RLS policies using the app's existing approved-device check.
2. Ensure `attachment_paths` stores object paths (or Supabase Storage URLs) consistently.
3. Test with sample objects and check the JSON response before enabling Cron.
4. Schedule a daily POST to `/functions/v1/cleanup-news-photos`, with `Authorization: Bearer <service-role-key>`, `apikey: <publishable-key>`, and `x-photo-cleanup-secret: <PHOTO_CLEANUP_SECRET>`. Keep the service-role key in server-side Cron configuration only.
5. Review Cron execution logs. Deletion is permanent; maintain an independent backup for important images.

## Important protection limitation
Protection is currently based on the news item's `priority` value being `high`, `critical`, or `urgent`. If editors need to mark a photo as important independently of the news priority, add an explicit protected-photo field and UI before enabling automatic cleanup.
