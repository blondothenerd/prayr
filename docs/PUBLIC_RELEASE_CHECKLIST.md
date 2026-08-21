# Public release checklist

Use this before the first public push and again before each release.

## 1. Create a clean public copy

Do not publish your working folder directly. Create a separate copy with no Git history and no local build artefacts.

The helper script in `tools/publicise_prayr.py` can do this.

## 2. Public identity

- [ ] App name is `prayr`.
- [ ] Author/maintainer is `blondothenerd`.
- [ ] Namespace/application ID uses `dev.blondothenerd.prayr` or another package you control.
- [ ] About screens, copyright strings, manifest labels, comments, docs, and sample data use public branding only.
- [ ] There are no employer or organisation names in the repository.

## 3. Remove private material

- [ ] No signing keys or keystores.
- [ ] No passwords, tokens, API keys, secrets, or `.env` files.
- [ ] No internal domains, hostnames, IP addresses, shares, drive letters, or absolute local paths.
- [ ] No personal prayer content or real names in sample data, screenshots, tests, or logs.
- [ ] No private email addresses.
- [ ] No build outputs (`.apk`, `.aab`, `build/`).
- [ ] No IDE state (`.idea/`, local Gradle caches).

## 4. Android checks

- [ ] `applicationId` is public-safe.
- [ ] `namespace` is public-safe.
- [ ] Source package declarations/imports match the new namespace.
- [ ] `AndroidManifest.xml` contains only required permissions and components.
- [ ] Notification channels use user-friendly public names.
- [ ] No vehicle/app-category spoofing is present.
- [ ] Release signing is configured outside the repository.

## 5. Privacy checks

- [ ] Review the dependency tree for analytics, advertising, remote logging, or cloud SDKs.
- [ ] Confirm the final build behaviour matches `PRIVACY.md`.
- [ ] Make screenshots use fake data only.

## 6. Build checks

```bash
./gradlew clean
./gradlew lint
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 7. GitHub setup

Recommended repository settings:

- Repository: `blondothenerd/prayr`
- Visibility: Public
- Default branch: `main`
- Issues: Enabled
- Discussions: Optional
- Actions: Enabled
- Secret scanning / push protection: Enable when available

Suggested topics:

`android`, `kotlin`, `prayer`, `notifications`, `open-source`, `privacy`

## 8. First commit

```bash
git init
git add .
git commit -m "Initial public release"
git branch -M main
git remote add origin https://github.com/blondothenerd/prayr.git
git push -u origin main
```

Do the final repository search before pushing. Git remembers embarrassing things forever; it is basically an elephant with a subpoena. 😂
