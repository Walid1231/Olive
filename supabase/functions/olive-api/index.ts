// Olive server API (Supabase Edge Function).
// Every call must carry the caller's Firebase ID token in "x-firebase-token".
//
//   upload        → one-time signed upload URL inside the caller's own folder
//   delete        → delete one of the caller's own files
//   livekit-token → join token for a Listen Together live room (host publishes, guests listen)
//
// Secrets: LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET (optional until live streaming is set up)
import { createClient } from "npm:@supabase/supabase-js@2";
import { createRemoteJWKSet, jwtVerify, SignJWT } from "npm:jose@5";

const FIREBASE_PROJECT = Deno.env.get("FIREBASE_PROJECT_ID") ?? "waleve-29f77";
const JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false },
});

const SHARES = "olive-shares";
const AVATARS = "olive-avatars";
const MEDIA_EXT = /\.(mp3|m4a|m4b|aac|opus|ogg|oga|flac|wav|webm|mp4|mka|wma|amr|3gp)$/i;
const MAX_SHARE = 50 * 1024 * 1024;

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-firebase-token",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

async function verifyFirebase(req: Request): Promise<{ uid: string; token: string }> {
  const token = req.headers.get("x-firebase-token") ?? "";
  if (!token) throw new Error("missing token");
  const { payload } = await jwtVerify(token, JWKS, {
    issuer: `https://securetoken.google.com/${FIREBASE_PROJECT}`,
    audience: FIREBASE_PROJECT,
  });
  const uid = typeof payload.sub === "string" ? payload.sub : "";
  if (!uid || uid.length > 128) throw new Error("bad token");
  return { uid, token };
}

/** Read a Firestore doc AS the caller (their security rules apply). */
async function firestoreGet(path: string, idToken: string) {
  const url = `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT}/databases/(default)/documents/${path}`;
  const r = await fetch(url, { headers: { Authorization: `Bearer ${idToken}` } });
  if (!r.ok) return null;
  return await r.json();
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "POST only" }, 405);

  let user: { uid: string; token: string };
  try {
    user = await verifyFirebase(req);
  } catch {
    return json({ error: "Please sign in again" }, 401);
  }
  const body = await req.json().catch(() => ({} as Record<string, unknown>));

  switch (body.action) {
    case "upload": {
      const avatar = body.kind === "avatar";
      const name = String(body.fileName ?? "file").replace(/[^A-Za-z0-9._-]/g, "_").slice(-120);
      const size = Number(body.size ?? 0);
      if (!avatar) {
        if (!MEDIA_EXT.test(name)) return json({ error: "Only audio files can be shared" }, 400);
        if (!(size > 0 && size <= MAX_SHARE)) return json({ error: "File is larger than 50 MB" }, 400);
      }
      const bucket = avatar ? AVATARS : SHARES;
      const rand = crypto.randomUUID().slice(0, 8);
      const path = avatar
        ? `avatars/${user.uid}/${Date.now()}_${rand}.jpg`
        : `shares/${user.uid}/${Date.now()}_${rand}_${name}`;
      const { data, error } = await admin.storage.from(bucket).createSignedUploadUrl(path);
      if (error || !data) return json({ error: error?.message ?? "Could not create upload" }, 500);
      const publicUrl = admin.storage.from(bucket).getPublicUrl(path).data.publicUrl;
      return json({ signedUrl: data.signedUrl, path, publicUrl });
    }

    case "delete": {
      const path = String(body.path ?? "");
      const bucket = body.bucket === AVATARS ? AVATARS : SHARES;
      const prefix = bucket === AVATARS ? `avatars/${user.uid}/` : `shares/${user.uid}/`;
      if (!path.startsWith(prefix) || path.includes("..")) return json({ error: "You can only delete your own files" }, 403);
      const { error } = await admin.storage.from(bucket).remove([path]);
      return error ? json({ error: error.message }, 500) : json({ ok: true });
    }

    case "livekit-token": {
      const lkUrl = Deno.env.get("LIVEKIT_URL");
      const key = Deno.env.get("LIVEKIT_API_KEY");
      const secret = Deno.env.get("LIVEKIT_API_SECRET");
      if (!lkUrl || !key || !secret) return json({ error: "Live streaming isn't set up yet" }, 503);

      const host = String(body.hostUid ?? "");
      if (!/^[A-Za-z0-9_-]{1,128}$/.test(host)) return json({ error: "Bad session" }, 400);
      // The caller's own Firestore permissions decide: only the host and invited friends can read it
      const doc = await firestoreGet(`sessions/${host}`, user.token);
      const f = doc?.fields ?? {};
      const active = f.active?.booleanValue === true;
      const invited: string[] = (f.invited?.arrayValue?.values ?? []).map((v: { stringValue?: string }) => v.stringValue ?? "");
      const isHost = user.uid === host;
      if (!doc || !active || (!isHost && !invited.includes(user.uid))) {
        return json({ error: "You're not in this session" }, 403);
      }
      const name = f.memberNames?.mapValue?.fields?.[user.uid]?.stringValue ??
        (isHost ? f.hostName?.stringValue : undefined) ?? "Friend";
      const room = `olive-${host}`;
      const now = Math.floor(Date.now() / 1000);
      const token = await new SignJWT({
        name,
        video: { room, roomJoin: true, canPublish: isHost, canPublishData: false, canSubscribe: true },
      })
        .setProtectedHeader({ alg: "HS256", typ: "JWT" })
        .setIssuer(key)
        .setSubject(user.uid)
        .setJti(`${user.uid}-${now}`)
        .setNotBefore(now - 10)
        .setExpirationTime(now + 6 * 3600)
        .sign(new TextEncoder().encode(secret));
      return json({ url: lkUrl, token, room });
    }

    default:
      return json({ error: "Unknown action" }, 400);
  }
});
