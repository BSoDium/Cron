# Signing: keeping debug and release installs data-compatible

Android only allows installing one APK over another (an "update", preserving app data) when both
are signed with the **same certificate**. A locally-run debug build signed with the auto-generated
debug keystore and a release APK signed with the real release keystore are different certificates
even though they share `applicationId = "fr.bsodium.cron"` — installing one over the other fails
with a signature mismatch, forcing an uninstall first, which wipes the Room DB / DataStore prefs.

## Fix: debug builds opt into the release key when it's available

`app/build.gradle.kts`'s `debug` build type signs with the `"release"` signing config whenever it's
actually configured (i.e. `storeFile != null`), falling back to the default debug keystore
otherwise. That fallback matters — CI and any other contributor building locally won't have your
release keystore, and must still get a working (debug-signed) build.

To opt in, add these four keys to your gitignored `local.properties` (same names the CI workflow
injects as env vars — see `.github/workflows/release.yml`):

```properties
STORE_FILE=/absolute/path/to/your/release.jks
STORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

`*.jks`/`*.keystore` are already gitignored repo-wide, so the keystore file itself is safe to keep
inside the repo root if that's convenient — it will never be committed.

Once set, every local debug build and every downloaded GitHub release APK carry the same
certificate: installing one over the other is a normal in-place update, no uninstall needed. The
first switch after adding this still needs one manual uninstall (the currently-installed app is
signed with the *old* debug key) — every switch after that is seamless.
