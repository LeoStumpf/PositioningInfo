# Play Console — answers for the declarations

What to enter under **Policy and programs › App content** for Positioning Info, and why. The
facts behind each answer are in [`PRIVACY.md`](../PRIVACY.md) and the manifest.

## Privacy policy

`https://github.com/LeoStumpf/PositioningInfo/blob/main/PRIVACY.md`

## Data safety

**Does your app collect or share any of the required user data types? → No.**

Google counts data as *collected* only when it is sent off the device. Positioning Info has no
`INTERNET` permission, so location, Wi-Fi/cell readings and the stored trip are processed and kept
on the phone only. With "No", the rest of the form (encryption in transit, deletion requests) is
skipped. Android's own network location and A-GNSS downloads are the system's, not the app's.

A GPX file you export is sent by your own action, to a place you choose — not collection either.

## Foreground service permissions

Needed because the manifest declares `FOREGROUND_SERVICE_LOCATION`.

- **Type:** Location
- **Task:** User-initiated — keeps GNSS readings running with the screen off while recording a
  trip, running the accuracy test, or filling the signal map. Off by default; the user switches it
  on after a disclosure dialog; a permanent notification with a Stop button is shown; it ends when
  the app is swiped away.
- **Why it can't be deferred or interrupted:** the user expects the recording to cover the whole
  time the screen is off; a paused or batched job would leave gaps in the track.
- **Video:** screen recording, uploaded to YouTube as unlisted:
  1. Speed page → tap *Background* → disclosure dialog → *Turn on*.
  2. Show the notification in the shade.
  3. Lock the screen, unlock: trip still recording.
  4. *Stop* in the notification → background mode off.

## Location permissions

Not needed: the app does not request `ACCESS_BACKGROUND_LOCATION`.

## App access

All functionality is available without special access (no login).

## Ads

No, the app contains no ads.

## Advertising ID

No. The app does not use the advertising ID, and no library adds the `AD_ID` permission (checked
in the merged release manifest).

## Content rating (IARC questionnaire)

Category *Utility, productivity, communication, or other*. Every content question is *No*,
including user interaction, sharing location with other users, and purchases. Expected rating:
Everyone / PEGI 3 / USK 0.

## Target audience and content

Target age **18 and over** (or 13+). Choosing an age group under 13 would bring the app under the
Families policy, which a technical GNSS tool has no need for. Not designed for children.

## Other declarations

- News app: no. Health app: no. Financial features: none. Government app: no.
- Data deletion: not applicable — no account, nothing collected; all data is deleted in the app
  (*Data on this phone*) or by uninstalling.

## Before production

- **Closed test first, if this is a personal developer account created after 13 Nov 2023:** at
  least 12 testers opted in for 14 consecutive days before production access can be requested.
- **Upload the bundle:** the `.aab` from the CI artifact (`positioning-info-<version>-<code>.aab`),
  signed with the upload key. Enroll in Play App Signing when the first bundle is uploaded.
- **Store listing:** category *Maps & Navigation* (or *Tools*); a contact email is required and
  shown publicly.
