# Building a signed APK

> **Note for this project:** the keystore was created as `ndot-release` - no file extension.
> A `*.jks` ignore rule does **not** match that, so it is now named explicitly in
> `.gitignore` too. Better still, move it out of the repo folder entirely (see the bottom of
> this file); a signing key living inside the project directory is one careless `git add -A`
> away from being published.

## Does one already exist?

No. The only APK in this project is a **debug** build at
`app/build/intermediates/apk/debug/app-debug.apk` — that's what Android Studio produced when
you hit Run. It *is* signed, but with the automatic debug key from
`C:\Users\The System Admin\.android\debug.keystore`, which every Android Studio install
generates for itself. There is no release keystore for this project yet.

There is one for a different project (`D:\Claude Code\Seamless\seamless-release.jks`) — don't
reuse it. One key per app keeps things clean.

## Do you actually need a signed release build?

For sideloading onto your own phone, the debug APK is enough. A release build is worth it for:

- no `debuggable` flag, and R8 available if you ever turn it on
- a signature **you** control, so future versions install over the old one
- anything involving the Play Store

**One catch:** the app currently on your phone is debug-signed. A release-signed build has a
different signature, so installing it over the top fails with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`. **Uninstall Ndot Widgets from the phone first**, then
install the release APK. After that, every future release build updates cleanly.

## Step 1 — create the keystore

Pick one of these. They do the same thing.

### Option A: the dialog you're already in

1. Click **Create new…**
2. **Key store path** — click the folder icon, go to `D:\Claude Code\NdotWidgets`, and give it
   the filename `ndot-release.jks`.
3. **Password** / **Confirm** — pick a keystore password. Write it down somewhere you won't
   lose it (see the warning at the bottom).
4. Under **Key**:
   - **Alias**: `ndot`
   - **Password** / **Confirm**: pick a key password (same as the keystore one is fine)
   - **Validity (years)**: `25` or more
   - **First and Last Name**: anything, e.g. your name. The rest of the certificate fields are
     optional and don't affect anything for sideloading.
5. **OK**, then **Next**.
6. Choose **release** as the build variant, then **Create**.

### Option B: the command line

Android Studio ships a JDK, so `keytool` is already on your machine:

```bash
cd "D:\Claude Code\NdotWidgets"
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore ndot-release.jks -alias ndot -keyalg RSA -keysize 2048 -validity 10000
```

It prompts for the passwords and the certificate details.

## Step 2 — wire it up (already done)

`app/build.gradle.kts` now reads signing credentials from `keystore.properties` at the project
root, so you never have to touch the dialog again. Create that file:

```bash
cd "D:\Claude Code\NdotWidgets"
cp keystore.properties.example keystore.properties
```

Then edit `keystore.properties` and put in the real passwords:

```properties
storeFile=ndot-release.jks
storePassword=whatever-you-chose
keyAlias=ndot
keyPassword=whatever-you-chose
```

Both `keystore.properties` and `*.jks` are gitignored, so the passwords and the key never get
committed.

## Step 3 — build it

Either from Android Studio (**Build → Generate Signed App Bundle or APK**, now that the
keystore exists), or, more simply, from the terminal:

```bash
cd "D:\Claude Code\NdotWidgets"
./gradlew assembleRelease
```

The APK lands at:

```
app/build/outputs/apk/release/app-release.apk
```

Copy that to the phone and install it. Or, with the phone connected:

```bash
./gradlew installRelease
```

## Shipping updates later

Bump both values in `app/build.gradle.kts` before each new build, or Android will refuse to
install it as an update:

```kotlin
versionCode = 2        // must increase every release
versionName = "1.1"    // the human-readable one
```

## Keep the keystore somewhere safer

Right now `ndot-release` sits in the project folder. Move it outside the repo and point
at it with an absolute path in `keystore.properties`:

```properties
storeFile=C:/Users/The System Admin/keys/ndot-release
```

(Forward slashes work fine on Windows and avoid backslash-escaping trouble.)

If you lose that file or forget its password, you can never update an already installed
copy of the app again - Android only accepts updates signed with the same key. Back it up
somewhere that isn't just this machine, and store the passwords with it.

**APK vs App Bundle:** the dialog offers both. Choose **APK**. App Bundles (`.aab`) are only
for uploading to the Play Store — you can't install one on a phone directly.
