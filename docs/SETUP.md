# Olive: build & release setup

## 1. Supabase (free file sharing for local songs/audiobooks)
1. Create a project at https://supabase.com (free plan).
2. **Storage → New bucket** → name `olive-shares` → turn **Public bucket** ON.
3. **SQL Editor** → run:
   ```sql
   create policy "olive anon upload"
   on storage.objects for insert to anon
   with check (bucket_id = 'olive-shares');
   ```
4. **Project Settings → API**: copy the *Project URL* and the *anon public* key.

Free plan limits: 1 GB of storage and 50 MB per file. Delete old files in the bucket now and then.

## 2. GitHub secrets (repo → Settings → Secrets and variables → Actions)
| Secret | Value |
|---|---|
| `GOOGLE_SERVICES_JSON` | full contents of your `app/google-services.json` |
| `SUPABASE_URL` | Supabase Project URL |
| `SUPABASE_ANON_KEY` | Supabase anon public key |
| `OLIVE_KEYSTORE_BASE64` | your keystore file as base64 (see below) |
| `OLIVE_KEYSTORE_PASSWORD` | keystore password |
| `OLIVE_KEY_ALIAS` | key alias |
| `OLIVE_KEY_PASSWORD` | key password |

Make the base64 text on Windows PowerShell:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\olive.jks")) | Set-Clipboard
```
If your old APKs were plain **debug** builds from Android Studio, use your debug keystore instead
(`C:\Users\<you>\.android\debug.keystore`, password `android`, alias `androiddebugkey`, key password `android`).
Android only accepts an update signed with the **same key** as the app already installed. A different key means you must uninstall first, which deletes your playlists.

## 3. Release
Push a tag such as `v1.1.0` (or Actions → *Build & Release APK* → Run workflow).
The APK is published at:
`https://github.com/Walid1231/Olive/releases/latest/download/Olive.apk`

## 4. Building on your laptop
Put the same values in `local.properties`:
```
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_ANON_KEY=eyJ...
```
