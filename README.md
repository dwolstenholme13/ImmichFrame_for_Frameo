<div align="center">
  <a href="https://github.com/dwolstenholme13/ImmichFrame_for_Frameo">
    <img src="https://github.com/immichFrame/ImmichFrame_Desktop/blob/main/src-tauri/icons/icon.png" alt="Logo" width="200" height="200">
  </a>

  <h3 align="center">ImmichFrame for Frameo</h3>

  <p align="center">
    Frameo-specific Android client for <a href="https://github.com/immichFrame/ImmichFrame">ImmichFrame</a>.
  <p>
</div>

This is a fork of [ImmichFrame Android](https://github.com/immichFrame/ImmichFrame_Android) that
specifically targets Frameo digital photo frame devices. This fork exists because its features for
controlling the screen power are not allowed in apps distributed in the Play Store, and so this
project cannot be merged with the upstream project. For Frameo devices, this doesn't matter because
the app cannot be installed from the Play Store anyway and must be directly loaded using ADB for
initial installation.

I will try to follow the upstream branch, but I've also made various changes of my own which I think
are better for use on these devices.

My Frameo device is running Android 13, but I have attempted to maintain backwards compatibility
with older Frameo devices. Please [raise an
Issue](https://github.com/dwolstenholme13/ImmichFrame_for_Frameo/issues) if you find a problem and I
will help as best I can.

## 📄 Documentation
The documentation for ImmichFrame is [here](https://immichframe.dev/docs/overview).  It includes
instructions for both the server component and the [Frameo
client](https://immichframe.dev/docs/getting-started/apps#frameo).

For this app on Frameo devices, I prefer using the [Frameo installation instructions from
ImmichKiosk](https://docs.immichkiosk.app/misc/frameo/). This project uses the ImmichFrame Android
app to connect to an ImmichKiosk backend, but the installation is the same whether you're using
ImmichFrame or ImmichKiosk on your server.

## Main differences from ImmichFrame Android
- Screen dimming function fully turns screen and backlight off, saving a lot of power
- Screen dimming function has "snooze" feature to wake up when power button pressed, and go back to
  sleep 1 minute later
- Screen keep-on is optional; if turned off, a setting is available in menu to set timeout
- App has update function in settings menu, to easily update to latest release from GitHub
- Settings menu shows current values for screen timeout, dimming time hours, and current app version

# Development
- [Android Studio](https://developer.android.com/studio/) 
