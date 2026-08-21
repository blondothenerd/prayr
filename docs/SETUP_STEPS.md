# Turn the existing prayr project into the public repo

These are the shortest safe steps.

## 1. Put this repo pack beside your existing project

Keep your current working project untouched.

## 2. Run the public-copy helper

From the repo pack directory:

```bash
python3 tools/publicise_prayr.py \
  "/path/to/current/prayr" \
  "/path/to/prayr-public" \
  --remove-term "YOUR_PRIVATE_ORG_NAME" \
  --remove-term "YOUR_PRIVATE_DOMAIN" \
  --old-package "YOUR.CURRENT.PACKAGE" \
  --new-package "dev.blondothenerd.prayr"
```

Repeat `--remove-term` for any private company, domain, hostname, product, share, or account name that must not appear publicly.

## 3. Copy this public repo metadata into the new app copy

Copy these files/folders into `/path/to/prayr-public`:

```text
README.md
LICENSE
NOTICE.md
PRIVACY.md
CONTRIBUTING.md
SECURITY.md
CHANGELOG.md
.gitignore
.editorconfig
.github/
docs/
```

Do not copy `PUBLICISATION_REPORT.txt` into Git.

## 4. Review the report

Open:

```text
/path/to/prayr-public/PUBLICISATION_REPORT.txt
```

Manually review every remaining URL, email, private IP, absolute path, and potential secret.

## 5. Search the whole public tree

Use your editor's global search for:

- old organisation names;
- old domains;
- internal hostnames;
- private IPs;
- old package IDs;
- personal email addresses;
- usernames;
- drive letters and network shares;
- API keys, tokens, passwords, and signing references.

## 6. Build it

```bash
cd /path/to/prayr-public
chmod +x gradlew
./gradlew clean
./gradlew lint
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Fix anything that still references the old namespace or a removed resource.

## 7. Initialise a brand-new Git history

```bash
rm -rf .git
git init
git add .
git commit -m "Initial public release"
git branch -M main
git remote add origin https://github.com/blondothenerd/prayr.git
git push -u origin main
```

A brand-new history matters: deleting a secret in a later commit does not delete it from earlier commits. Git is extremely loyal to your worst decisions. 😂

## 8. GitHub settings

Use:

- **Repo:** `prayr`
- **Owner:** `blondothenerd`
- **Description:** `A simple Android prayer companion with configurable local notifications.`
- **Topics:** `android`, `kotlin`, `prayer`, `notifications`, `privacy`, `open-source`
- **License:** MIT

Enable Actions, Issues, and secret scanning/push protection if available.
