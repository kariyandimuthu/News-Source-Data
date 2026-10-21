import { createClient } from "npm:@supabase/supabase-js@2";

const MiB = 1024 * 1024;
const triggerBytes = 800 * MiB;
const targetBytes = 700 * MiB;
const pageSize = 100;

type StoredObject = {
  name: string;
  id?: string;
  created_at?: string;
  updated_at?: string;
  metadata?: { size?: number | string; mimetype?: string } | null;
};

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "POST required" }, 405);

  const expectedSecret = Deno.env.get("PHOTO_CLEANUP_SECRET") ?? "";
  const suppliedSecret = req.headers.get("x-photo-cleanup-secret") ?? "";
  if (!expectedSecret || suppliedSecret.length !== expectedSecret.length ||
      !constantTimeEqual(suppliedSecret, expectedSecret)) {
    return json({ error: "Unauthorized" }, 401);
  }

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const bucket = Deno.env.get("NEWS_PHOTO_BUCKET");
  if (!url || !serviceKey || !bucket) {
    return json({ error: "Missing server configuration" }, 500);
  }

  const supabase = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // Protect paths attached to explicitly important news. Never delete database rows.
  const protectedPaths = new Set<string>();
  const { data: protectedNews, error: protectedError } = await supabase
    .from("news_items")
    .select("attachment_paths")
    .in("priority", ["high", "critical", "urgent"]);
  if (protectedError) return json({ error: "Could not load protected news paths" }, 500);
  for (const row of protectedNews ?? []) {
    for (const value of (row.attachment_paths ?? []) as unknown[]) {
      if (typeof value === "string" && value.trim()) protectedPaths.add(normalizePath(value));
    }
  }

  const allObjects: Array<{ path: string; bytes: number; createdAt: number }> = [];
  async function walk(prefix: string): Promise<void> {
    for (let offset = 0; ; offset += pageSize) {
      const { data, error } = await supabase.storage.from(bucket).list(prefix, {
        limit: pageSize,
        offset,
        sortBy: { column: "created_at", order: "asc" },
      });
      if (error) throw new Error("Storage listing failed");
      const entries = (data ?? []) as StoredObject[];
      for (const entry of entries) {
        const path = prefix ? prefix + "/" + entry.name : entry.name;
        const isFolder = !entry.id && !entry.metadata;
        if (isFolder) {
          await walk(path);
          continue;
        }
        const bytes = Number(entry.metadata?.size ?? 0);
        allObjects.push({
          path,
          bytes: Number.isFinite(bytes) && bytes > 0 ? bytes : 0,
          createdAt: Date.parse(entry.created_at ?? entry.updated_at ?? "") || 0,
        });
      }
      if (entries.length < pageSize) break;
    }
  }

  try {
    await walk("");
  } catch {
    return json({ error: "Could not inspect Storage; no files were deleted" }, 502);
  }

  let totalBytes = allObjects.reduce((sum, object) => sum + object.bytes, 0);
  if (totalBytes <= triggerBytes) {
    return json({
      status: "below_threshold",
      bucket,
      total_bytes: totalBytes,
      trigger_bytes: triggerBytes,
      deleted_files: 0,
      protected_files: protectedPaths.size,
    });
  }

  const candidates = allObjects
    .filter((object) => !protectedPaths.has(normalizePath(object.path)))
    .sort((a, b) => a.createdAt - b.createdAt);

  const deleted: string[] = [];
  const failed: string[] = [];
  for (let i = 0; i < candidates.length && totalBytes > targetBytes; i += 100) {
    const batch = candidates.slice(i, i + 100);
    const { error } = await supabase.storage.from(bucket).remove(batch.map((object) => object.path));
    if (error) {
      failed.push(...batch.map((object) => object.path));
      continue;
    }
    for (const object of batch) {
      deleted.push(object.path);
      totalBytes -= object.bytes;
    }
  }

  return json({
    status: failed.length ? "completed_with_errors" : totalBytes > targetBytes ? "protected_files_exceed_target" : "completed",
    bucket,
    trigger_bytes: triggerBytes,
    target_bytes: targetBytes,
    remaining_bytes_estimate: Math.max(0, totalBytes),
    deleted_files: deleted.length,
    deleted_bytes_estimate: allObjects.filter((object) => deleted.includes(object.path)).reduce((sum, object) => sum + object.bytes, 0),
    protected_files: protectedPaths.size,
    failed_files: failed.length,
  });
});

function normalizePath(value: string): string {
  // attachment_paths may contain a Storage object path or a full Supabase Storage URL.
  const marker = "/object/";
  const index = value.indexOf(marker);
  if (index >= 0) {
    const after = value.slice(index + marker.length);
    const slash = after.indexOf("/");
    return slash >= 0 ? decodeURIComponent(after.slice(slash + 1)).replace(/^\/+/, "") : value;
  }
  return value.replace(/^\/+/, "");
}

function constantTimeEqual(a: string, b: string): boolean {
  let diff = a.length ^ b.length;
  const length = Math.max(a.length, b.length);
  for (let i = 0; i < length; i++) {
    diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  }
  return diff === 0;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
