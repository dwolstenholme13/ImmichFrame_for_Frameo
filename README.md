<div align="center">
  <a href="https://github.com/dwolstenholme13/ImmichFrame_for_Frameo">
    <img src="https://github.com/immichFrame/ImmichFrame_Desktop/blob/main/src-tauri/icons/icon.png" alt="Logo" width="200" height="200">
  </a>

  <h3 align="center">ImmichFrame for Frameo</h3>

  <p align="center">
    Frameo-specific Android client for <a href="https://github.com/immichFrame/ImmichFrame">ImmichFrame</a>.
  <p>
</div>

This is a fork of [ImmichFrame Android](https://github.com/immichFrame/ImmichFrame_Android)
specifically targeting Frameo devices. This project cannot be merged with the upstream project
because its features for controlling the screen power are not allowed in apps distributed in the
Play Store. For Frameo devices, this doesn't matter because the app is not distributed this way, and
must be directly loaded using ADB.

I will try to follow the upstream branch, but I've also made various changes of my own which
I think are better for use on these devices.

My Frameo device is running Android 13, but I have attempted to maintain backwards compatibility
with older Frameo devices. Please raise an Issue if you find a problem and I will help as best I
can.

## 📄 Documentation
You can find the documentation for the original ImmichFrame Android project [here](https://immichframe.dev).

# Development
- [Android Studio](https://developer.android.com/studio/) 
