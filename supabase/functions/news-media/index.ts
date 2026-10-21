import { createClient } from "npm:@supabase/supabase-js@2";

const BUCKET = "news-photos";
const MAX_PHOTO_BYTES = 4 * 1024 * 1024;

type ActionBody = {
  action?: unknown;
  file_base64?: unknown;
  path?: unknown;
};

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "POST required" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) return json({ error: "Server storage is not configured" }, 500);

  const authorization = req.headers.get("authorization") ?? "";
  const token = authorization.match(/^Bearer\s+(.+)$/i)?.[1] ?? "";
  const deviceId = req.headers.get("x-app-device-id") ?? "";
  const deviceSecret = req.headers.get("x-app-device-secret") ?? "";
  if (!token || !deviceId || deviceSecret.length < 64) {
    return json({ error: "Login and registered device credentials are required" }, 401);
  }

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // Validate the user JWT, then independently enforce the device approval and session cut-off.
  const { data: userResult, error: userError } = await admin.auth.getUser(token);
  const user = userResult.user;
  if (userError || !user) return json({ error: "Invalid or expired login session" }, 401);

  let issuedAt = 0;
  try {
    const segment = token.split(".")[1];
    if (!segment) throw new Error("Missing JWT payload");
    const normalized = segment.replace(/-/g, "+").replace(/_/g, "/");
    const payload = JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=")));
    issuedAt = Number(payload.iat);
  } catch {
    return json({ error: "Unable to validate session timestamp" }, 401);
  }
  if (!Number.isFinite(issuedAt) || issuedAt <= 0) {
    return json({ error: "Session timestamp is missing" }, 401);
  }

  const { data: cutoff, error: cutoffError } = await admin
    .from("app_security_control")
    .select("sessions_valid_after")
    .eq("singleton", true)
    .maybeSingle();
  if (cutoffError || !cutoff?.sessions_valid_after) {
    return json({ error: "Session security control could not be verified" }, 503);
  }
  if (issuedAt * 1000 < Date.parse(cutoff.sessions_valid_after)) {
    return json({ error: "This login session has been revoked. Sign in again." }, 401);
  }

  const { data: device, error: deviceError } = await admin
    .from("app_devices")
    .select("device_id,auth_user_id,status,secret_hash")
    .eq("device_id", deviceId)
    .eq("auth_user_id", user.id)
    .maybeSingle();
  if (deviceError || !device || !["owner", "approved"].includes(device.status)) {
    return json({ error: "This device is not approved" }, 403);
  }

  const secretDigest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(deviceSecret));
  const suppliedHash = Array.from(new Uint8Array(secretDigest), (byte) => byte.toString(16).padStart(2, "0")).join("");
  if (!constantTimeEqual(suppliedHash, device.secret_hash)) {
    return json({ error: "Device identity verification failed" }, 403);
  }

  let body: ActionBody;
  try {
    body = await req.json() as ActionBody;
  } catch {
    return json({ error: "Invalid JSON request" }, 400);
  }

  const action = typeof body.action === "string" ? body.action : "";
  if (action === "upload") {
    if (typeof body.file_base64 !== "string" || body.file_base64.length === 0) {
      return json({ error: "Photo data is required" }, 400);
    }
    if (body.file_base64.length > Math.ceil(MAX_PHOTO_BYTES * 4 / 3) + 8) {
      return json({ error: "Photo exceeds the 4 MB upload limit" }, 413);
    }
    let bytes: Uint8Array;
    try {
      bytes = Uint8Array.from(atob(body.file_base64), (ch) => ch.charCodeAt(0));
    } catch {
      return json({ error: "Photo data is not valid base64" }, 400);
    }
    if (bytes.length > MAX_PHOTO_BYTES) return json({ error: "Photo exceeds the 4 MB upload limit" }, 413);
    if (bytes.length < 3 || bytes[0] !== 0xff || bytes[1] !== 0xd8 || bytes[2] !== 0xff) {
      return json({ error: "Only compressed JPEG photos are accepted" }, 415);
    }

    const objectPath = `${user.id}/${crypto.randomUUID()}.jpg`;
    const { error } = await admin.storage.from(BUCKET).upload(objectPath, bytes, {
      contentType: "image/jpeg",
      upsert: false,
    });
    if (error) return json({ error: "Photo upload failed" }, 502);
    return json({ path: objectPath });
  }

  if (action === "download") {
    const objectPath = validateObjectPath(body.path);
    if (!objectPath) return json({ error: "Invalid photo path" }, 400);
    const { data, error } = await admin.storage.from(BUCKET).download(objectPath);
    if (error || !data) return json({ error: "Photo could not be found or downloaded" }, 404);
    return new Response(data, {
      status: 200,
      headers: {
        "Content-Type": "image/jpeg",
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff",
      },
    });
  }

  if (action === "delete") {
    const objectPath = validateObjectPath(body.path);
    if (!objectPath) return json({ error: "Invalid photo path" }, 400);
    // Shared approved devices use the same app account. Still restrict deletion to its user namespace.
    if (!objectPath.startsWith(`${user.id}/`)) return json({ error: "Photo deletion is not permitted" }, 403);
    const { error } = await admin.storage.from(BUCKET).remove([objectPath]);
    if (error) return json({ error: "Photo deletion failed" }, 502);
    return json({ ok: true });
  }

  return json({ error: "Unsupported action" }, 400);
});

function validateObjectPath(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const path = value.trim();
  if (!path || path.startsWith("/") || path.includes("\\") || path.split("/").some((part) => part === "." || part === "..")) {
    return null;
  }
  if (path.length > 512 || !path.toLowerCase().endsWith(".jpg")) return null;
  return path;
}

function constantTimeEqual(a: string, b: string): boolean {
  let diff = a.length ^ b.length;
  const length = Math.max(a.length, b.length);
  for (let i = 0; i < length; i++) diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  return diff === 0;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}
