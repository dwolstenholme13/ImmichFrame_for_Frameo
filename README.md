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

## Background

[Immich](https://immich.app/) is a self-hosted photo management application that is very similar to
Google Photos. [ImmichFrame](https://immichframe.dev/) is a companion application which fetches
photos from your Immich albums, and displays them in a web browser, turning any device with a web
browser into a digital photo frame. Frameo is a software vendor that partners with various digital
photo frame makers, preloading their software onto Android-based photo frames; their business model
seems to rely on selling subscriptions to ther cloud service so that their frames can be easily
managed remotely, something very attractive to people who want to share photo albums with their
retired parents. The nice thing about Frameo frames is they're relatively inexpensive, and can be
easily unlocked to work with [ADB](https://en.wikipedia.org/wiki/Android_Debug_Bridge), so that
alternative software can be loaded. ImmichFrame for Frameo works by taking the place of the original
Frameo app, and then connecting to an ImmichFrame instance running on your home server, which then
fetches photos from your Immich albums and displays them on the Frameo device.

## Documentation 📄
The documentation for ImmichFrame is [here](https://immichframe.dev/docs/overview).  It includes
instructions for both the server component and the [Frameo
client](https://immichframe.dev/docs/getting-started/apps#frameo).

For loading this app on Frameo devices, I prefer using the [Frameo installation instructions from
ImmichKiosk](https://docs.immichkiosk.app/misc/frameo/). This project uses the ImmichFrame Android
app to connect to an ImmichKiosk backend, but the installation is the same whether you're using
ImmichFrame or ImmichKiosk on your server.

Be sure to also enable the following permissions using ADB:
`adb shell appops set com.immichframe.immichframe REQUEST_INSTALL_PACKAGES allow`
`adb shell appops set com.immichframe.immichframe WRITE_SETTINGS allow`

## Main differences from ImmichFrame Android
- Screen dimming function fully turns screen and backlight off, saving a lot of power
- Screen dimming function has "snooze" feature to wake up when power button pressed, and go back to
  sleep 1 minute later
- Screen keep-on is optional; if turned off, a setting is available in menu to set timeout
- App has update function in settings menu, to easily update to latest release from GitHub
- Settings menu shows current values for screen timeout, dimming time hours, and current app version

## Tested devices
- moonka ZN-DP1101 (build ID SSA_ZN-DP1101-20251205, webView v101.0.4951.61)
- moonka ZN-DP1101 (build ID SSA_ZN-DP1101-20251205, webView v106.0.5249.126)

## Development
- [Android Studio](https://developer.android.com/studio/) 
