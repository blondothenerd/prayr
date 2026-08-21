# Privacy

Prayer requests may contain sensitive personal information, so the public edition of `prayr` is intended to be local-first.

## Public-build privacy rules

The public build should:

- store prayer entries and app settings on the device;
- avoid advertising SDKs;
- avoid analytics and behavioural tracking;
- avoid remote application logging containing prayer text;
- avoid requiring an account for core use;
- document any future network, backup, sync, analytics, or crash-reporting feature before it is enabled.

## Notifications

Notification text may be visible on the lock screen, connected displays, watches, or vehicle interfaces depending on the user's Android settings. Users should choose notification privacy settings appropriate for the sensitivity of their prayer content.

## Before publishing an APK

Verify the final manifest and dependency tree against this policy. If a future release adds any data transmission, update this document before publishing that release.

## Contact

For privacy questions, open an issue in the `blondothenerd/prayr` GitHub repository without including private prayer content.
