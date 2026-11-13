# Android phone and TV validation checklist

Run the automated suite before installing a build:

```bash
./gradlew testDebugUnitTest assembleRelease
```

## Phone smoke test

- [ ] Install the release APK over the previous version and confirm My List and playback history remain.
- [ ] Open a local video playlist containing at least three files.
- [ ] Confirm the player starts at the selected item and displays the correct title.
- [ ] Let an episode reach its end; confirm Media3 advances to the next queue item automatically.
- [ ] Use Previous and Next in the player controls; verify first/last-item boundaries.
- [ ] Open Subtitles and select an embedded text track; verify Off works.
- [ ] Open Quality on a multi-rendition HLS/DASH test stream; select a lower and higher rendition.
- [ ] Verify a single-rendition MP4/HLS stream does not falsely imply that multiple qualities exist.
- [ ] Background the app and return; verify playback pauses on stop and resumes only by user action.
- [ ] Rotate the device and use Back to exit the player.

## Android TV / remote test

- [ ] Install on a physical Android TV or certified Android TV box.
- [ ] Navigate Home, Discover, Search, My List, Settings and Player using only the D-pad.
- [ ] Confirm every actionable card/button receives a visible focus state.
- [ ] Open an anime detail page, move focus between episode cards, and launch an episode.
- [ ] During playback, use D-pad/OK to reveal PlayerView controls.
- [ ] Verify Previous, Next, Quality and Subtitles can be focused and activated with the remote.
- [ ] Verify Back exits dialogs first, then exits playback.
- [ ] Test a remote with dedicated media keys if available.
- [ ] Repeat with a slow Wi-Fi connection and a low-memory TV box.

## Provider verification

- [ ] Confirm Kitsu returns episode metadata for exact title matches.
- [ ] Confirm Kitsu entries open their Kitsu episode pages; they are catalogue links, not playable streams.
- [ ] Confirm AniList and Kitsu results group without duplicating equivalent episodes.
- [ ] Confirm native playback is offered only for provider-returned HTTPS streams explicitly marked rights-confirmed.

Record device model, Android version, app version, network, stream format, and pass/fail for every test. Automated CI cannot substitute for physical-device playback and remote testing.
