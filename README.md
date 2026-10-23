# Form Helper (Android)

A helper that floats over other apps on your phone and, when you tap its buttons:

- **Capture** reads "Label: value" pairs and already-filled boxes on the screen
- **Fill** types your saved data into matching text boxes and picks matching dropdown options
- **Next** taps Next/Continue (for Submit/Save you must tap twice)

It does nothing on its own. You tap, it acts, you check.

## Get the APK (no computer needed)

1. Create a free GitHub account, make a new repository, upload everything in this folder
   (keep the `.github/workflows/build.yml` path).
2. Open the **Actions** tab, run **Build APK**, wait about 3 minutes.
3. Open the finished run, download **FormHelper-apk**, unzip, install `app-debug.apk`
   (allow "install unknown apps" for your browser/files app).

Or open the folder in Android Studio on a PC and press Run.

## First-time setup on the phone

1. Open **Form Helper** and tap *Turn on Form Helper (Accessibility)*. Choose it in the list and switch on.
2. If Android shows "Restricted setting": Settings > Apps > Form Helper > ⋮ > *Allow restricted settings*, then retry.
3. Type your data in the profile box, one line per field:

   ```
   label|other name|name in another language = value
   ```

   Use the label exactly as the form shows it, and add other spellings after `|`. Matching ignores case,
   spaces, punctuation and works with Telugu or any other script.

## Using it

1. Open the other app and go to the form. A small panel appears on the right edge.
2. Tap **Fill**. A message lists what was filled, what failed and which fields had no data.
3. Dropdowns: the bot opens the list, scrolls if needed and taps the matching option.
   If an option isn't found it stops and tells you, so you can pick it yourself.
4. Tap **Next** to move on. Check every screen yourself before submitting.

## Limits

- Apps that block accessibility reading (some banking or secure screens) show nothing.
- Fields that are custom-drawn (not normal Android views) can't be read.
- Matching is by label text; if a form's labels differ from your keys, add the extra name after `|`.
