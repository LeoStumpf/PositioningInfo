# Play Console upload guide — Positioning Info 1.1.0

Follow the steps in order. Every value to enter is given here. Longer texts are in
[`store-listing.md`](store-listing.md), and the reasons behind each declaration are in
[`play-console.md`](play-console.md).

## Facts at a glance

| | |
|---|---|
| App name | Positioning Info |
| Package name | `io.github.leostumpf.positioninginfo` (fixed forever after the first upload) |
| Version | 1.1.0. The version code comes from the commit time; `./gradlew -q printVersion` shows it |
| Default language | English (United States), en-US |
| App or game | App |
| Free or paid | Free. This cannot be changed to paid later |
| Category | Maps & Navigation |
| Tags | Navigation, Tools (choose up to 5 that fit) |
| Contact email | *your public developer email* (required, shown on the store page) |
| Website | `https://github.com/LeoStumpf/PositioningInfo` |
| Privacy policy | `https://github.com/LeoStumpf/PositioningInfo/blob/main/PRIVACY.md` |
| Ads | None |
| Advertising ID | Not used |
| Permissions | Location (fine and coarse), location extra commands, Wi-Fi state (read and change), network state, foreground service (location), notifications. No internet |
| Target SDK / min SDK | 36 / 26 (Android 8.0) |
| Upload file | `positioning-info-1.1.0-<code>.aab` from the CI run of the commit you ship |

## 1. Create the app

**Home › Create app**

1. App name: `Positioning Info`
2. Default language: English (United States)
3. App or game: **App**. Free or paid: **Free**
4. Tick both declarations (Developer Program Policies, US export laws) › **Create app**

## 2. App content (Policy and programs › App content)

Work through every card. Each one must show a green tick before the closed test can go out.

| Card | Answer |
|---|---|
| **Privacy policy** | The URL above |
| **App access** | All functionality is available without special access |
| **Ads** | No, my app does not contain ads |
| **Content rating** | Start the questionnaire. Email: yours. Category: *All other app types*. Answer **No** to every question, including user interaction, shares location with other users, and digital purchases. Submit. Expected result: Everyone / PEGI 3 / USK 0 |
| **Target audience and content** | Age group **18 and over** only. The store listing does not appeal to children: **No** |
| **News app** | No |
| **Data safety** | See below |
| **Government app** | No |
| **Financial features** | My app doesn't provide any financial features |
| **Health** | My app does not have any health features |
| **Advertising ID** | No, the app does not use the advertising ID |
| **Foreground service permissions** | See below |

### Data safety

1. *Does your app collect or share any of the required user data types?* **No**
2. With "No", Play skips the questions on encryption and deletion.
3. Review the summary, which should say "No data collected / No data shared", then **Save**.

The reason: nothing leaves the phone, because the app has no internet permission. A GPX export
goes where the user sends it, so it counts as neither collection nor sharing.

### Foreground service permissions

1. Tick **Location**.
2. Task: **Other** (German: *Sonstiger*). The other choices do not fit: *Background location
   updates* suggests the background location permission, which the app never requests;
   *User-initiated location sharing* means sending the location to others; *Navigation* and
   *Geofencing* are not what the app does.
3. Description (paste):

   ```
   Background mode is off by default. The user switches it on after a disclosure dialog, to keep a trip recording, the accuracy test or the signal map running with the screen off. A permanent notification with a Stop button is shown for the whole time, and it ends when the app is closed. The app never requests background location permission.
   ```
4. If the form asks for a video link, record the steps in `play-console.md` (Speed › Background ›
   Turn on › notification › Stop) and add the link as an unlisted YouTube video.

## 3. Store listing (Grow users › Store presence › Main store listing)

| Field | Take it from |
|---|---|
| App name | `store-listing.md` › App name |
| Short description | `store-listing.md` › Short description (74 of 80 characters) |
| Full description | `store-listing.md` › Full description (2 845 of 4 000 characters) |
| App icon | `docs/brand/play-icon-512.png` |
| Feature graphic | `docs/brand/play-feature-1024x500.png` |
| Phone screenshots | `docs/store/phone/`: all six, in file-name order |
| 7-inch tablet screenshots | `docs/store/tablet7/`: all six, in file-name order |
| 10-inch tablet screenshots | `docs/store/tablet10/`: all six, in file-name order |
| Video | leave empty |

**Save.** Then open **Store settings** and set the category, the tags and the contact details from
the table above.

## 4. The bundle

1. GitHub › Actions › **CI** › open the latest green run on `main`. Check that **both** jobs,
   `build` and `ui-tests`, are green.
2. Under **Artifacts**, download `positioning-info-<commit>` and unzip it. It contains the
   `.aab`, the `.apk` and R8's `mapping.txt`.
3. That run also tagged the commit `build-<versionCode>`.

## 5. First release: internal testing

Internal testing needs no review and is available at once. Use it to see the pre-launch report
before any tester does.

1. **Test and release › Testing › Internal testing › Create new release**
2. App signing: accept **Google Play App Signing**. Your keystore now counts as the *upload key*.
   Keep a backup of the keystore file and its passwords off this laptop.
3. Upload the `.aab`. Release name: `1.1.0`
4. Release notes: `store-listing.md` › Release notes
5. **Testers** tab: create an email list with yourself. Copy the opt-in link and install the app
   from the Play Store on your phone.
6. **Save › Review release › Start rollout to Internal testing**
7. About an hour later, check **Test and release › Pre-launch report**: crashes, accessibility and
   screenshots on Google's devices.
8. Upload `mapping.txt` under **App bundle explorer › Downloads › ReTrace mapping file**, if Play
   did not read it from the bundle, so crash traces are readable.

## 6. Closed testing

1. **Test and release › Testing › Closed testing › Create track** (or use the default *Alpha*
   track).
2. **Countries/regions**: add the countries your testers are in (for example Germany, Austria,
   Switzerland, United Kingdom, United States).
3. **Testers**: an email list, or a Google Group, which is easier to grow. Add the feedback
   channel: your email or `https://github.com/LeoStumpf/PositioningInfo/issues`.
4. **Create new release** › *Add from library*: choose the same bundle as internal testing.
   Release notes as before.
5. **Review release › Start rollout to Closed testing**. The first closed release goes through
   Google's review, which usually takes one to three days, sometimes up to seven.
6. After approval, send the testers the **opt-in link** from the Testers tab. Each tester must
   accept on the web page and then install from the Play Store.

### Production access (personal accounts created after 13 November 2023)

- At least **12 testers** must stay opted in for **14 days in a row**. Recruit about 15, because
  someone always drops out.
- Afterwards, **Dashboard › Apply for production** asks how the test went and what changed.
  Keep notes of tester feedback and of the fixes you shipped during the test.
- Updates during the test are fine: each push to `main` builds a higher version code. Upload the
  new `.aab` to the closed track.

## 7. After each upload

- The version code must be higher than the last upload. CI takes care of that, as long as you
  upload the bundle of a later commit.
- Raise `appVersionName` in `gradle.properties` before a release that users should see as new
  (1.1.1, 1.2.0 and so on).

## Reference

| What | Where |
|---|---|
| Store texts | `docs/store-listing.md` |
| Declarations, with reasons | `docs/play-console.md` |
| Privacy policy | `PRIVACY.md` |
| Brand graphics | `docs/brand/` |
| Screenshots | `docs/store/` (recreate them with the debug demo mode, see `CLAUDE.md`) |
