#!/bin/sh
# Installation script for ImmichFrame for Frameo app
# Must be run with device plugged into suitable USB-C cable

# TODO:
# - detect if ImmichFrame Android already installed; prompt user to allow removing it before proceeding
# - detect if ImmichFrame for Frameo already installed; warn user that it's already installed, but can be re-installed here if they wish: use "-r" if they want to save settings, or otherwise remove existing app and re-install
# - prompt user to enable ADB in Frameo app
# - prompt user to configure Wi-Fi in Android settings (adb shell am start -a android.settings.SETTINGS)


# check for device, capture serial number; if multiple devices, have user select one;
# if only "unauthorized" device(s) present, warn user and suggest fix with Frameo app;
# use this with "-s <serial>" in all commands
select_device() {
    # capture Android devices and their states
    devices=$(
        adb devices |
        awk 'NR > 1 && $1 != "" {
            print $1, $2
        }'
    )

    authorized=$(
        printf '%s\n' "$devices" |
        awk '$2 == "device" { print $1 }'
    )

    unauthorized=$(
        printf '%s\n' "$devices" |
        awk '$2 == "unauthorized" { print $1 }'
    )

    count=$(printf '%s\n' "$authorized" | grep -c .)
    unauthorized_count=$(printf '%s\n' "$unauthorized" | grep -c .)

    if [ "$count" -eq 0 ]; then
        if [ "$unauthorized_count" -gt 0 ]; then
            echo "Error: No authorized Android devices found." >&2
            echo >&2
            echo "The following device(s) are unauthorized:" >&2
            printf '  %s\n' "$unauthorized" >&2
            echo >&2
            echo "Try disabling and re-enabling ADB debugging in the Frameo app." >&2
        else
            echo "Error: No Android devices connected." >&2
        fi
        return 1

    elif [ "$count" -eq 1 ]; then
        # only one authorized device found, so use it
        serial=$authorized

    else
        # multiple authorized devices found, so make user choose one
        echo "Multiple devices detected:" >&2
        echo >&2

        i=1
        for d in $authorized
        do
            echo "  $i) $d" >&2
            i=$((i + 1))
        done

        echo >&2
        printf 'Choose device number: ' >&2
        read choice

        case "$choice" in
            ''|*[!0-9]*)
                echo "Invalid selection." >&2
                return 1
                ;;
        esac

        i=1
        serial=

        for d in $authorized
        do
            if [ "$i" -eq "$choice" ]; then
                serial=$d
                break
            fi
            i=$((i + 1))
        done

        if [ -z "$serial" ]; then
            echo "Invalid selection." >&2
            return 1
        fi
    fi

    # stdout is reserved for the selected serial number
    printf '%s\n' "$serial"
}


# find version number of Android OS, and if it's not at least 13, verify with user
os_version_check() {
    # get version number, remove CR
    version=$(adb -s "$serial" shell getprop ro.build.version.release)
    version=$(printf '%s' "$version" | tr -d '\r')

    # extract major version (handles "13", "13.0", "14.1.2", etc.)
    major=${version%%.*}

    # verify it's numeric
    case "$major" in
        ''|*[!0-9]*)
            echo "Unable to determine Android version: $version" >&2
            return 1
            ;;
    esac

    # verify major number is at least 13, and if not only proceed if user OKs
    if [ "$major" -lt 13 ]; then
        echo
        echo "Warning: Device is running Android $version."
        echo "This app is only tested on Android 13."
        echo

        printf 'Continue anyway? [y/N]: '
        read answer

        case "$answer" in
            y|Y|yes|YES)
                echo "Continuing..."
                ;;
            *)
                echo "Aborted."
                return 1
                ;;
        esac
    fi

    # output full version number
    printf '%s\n' "$version"
}


install_check() {
    pkg="com.immichframe.immichframe"

    app_version=$(
        adb -s "$serial" shell dumpsys package "$pkg" 2>/dev/null |
        awk -F= '/versionName=/ { print $2; exit }' |
        tr -d '\r'
    )

    # if not yet installed, just proceed
    if [ -z "$app_version" ]; then
        return 0
    fi

    echo "ImmichFrame app version $app_version currently installed."
    printf 'Replace app? [y/N]: '
    read answer

    case "$answer" in
        y|Y|yes|YES)
            ;;
        *)
            echo "Aborted."
            return 1
            ;;
    esac

    printf 'Keep existing app data? [y/N]: '
    read answer
    install_opt=

    case "$answer" in
        y|Y|yes|YES)
            install_opt=" -r"
            ;;
    esac

    # output install option flag
#    printf(
# TODO haven't finished
}

# start of program

# verify argument present or show usage
if [ $# -ne 1 ]; then
    echo "Usage: $0 <filename.apk>"
    exit -1
fi

# verify input file
cmdret=$(file "$1")
search="Android package (APK)"
case "$cmdret" in
    *"$search"*)
        echo "Installing APK package $1"
        ;;
    *)
        echo "File $1 is not a valid APK package"
        exit -1
        ;;
esac

# verify Android device connected and get serial number
serial=$(select_device) || exit -1
echo "Using Android device: $serial"

# find Android version
version=$(os_version_check) || exit -1

# find build ID
buildid=$(adb -s "$serial" shell getprop ro.build.display.id)

# find webview version
webview_version=$(
    adb -s "$serial" shell dumpsys webviewupdate | 
    grep -i "Current WebView package" |
    sed -n 's/.*(.*,\s*\([^)][^)]*\)).*/\1/p' |
    tr -d '\r'
)

# set persistent USB configuration for ADB
adb -s "$serial" shell setprop persist.sys.usb.config adb

# ensure ADB and developer settings stay enabled
adb -s "$serial" shell settings put global adb_enabled 1
adb -s "$serial" shell settings put global development_settings_enabled 1

# verify USB configuration is persistent
cmdret=$(adb -s "$serial" shell getprop persist.sys.usb.config)
if [ "$cmdret" != "adb" ]; then
    echo "Failed to set USB configuration persistence with ADB"
    exit -1
fi

# install APK # TODO maybe verify somehow
if ! adb -s "$serial" install $1; then
    echo "APK installation failed!" >&2
    exit -1
fi

# start ImmichFrame app
adb -s "$serial" shell am start -n com.immichframe.immichframe/.MainActivity

# disable Frameo app
adb -s "$serial" shell pm disable-user --user 0 net.frameo.frame

# set ImmichFrame app to auto-start on boot
cmdret=$(adb -s "$serial" shell cmd package set-home-activity com.immichframe.immichframe/.MainActivity)
if [ "$cmdret" != "Success" ]; then
    echo "Failed to set ImmichFrame for Frameo app to auto-start on boot"
    exit -1
fi

# disable spell-checker so entering domain names is easier
adb -s "$serial" shell settings put secure spell_checker_enabled 0

# enable installing packages so app can auto-update
adb -s "$serial" shell appops set com.immichframe.immichframe REQUEST_INSTALL_PACKAGES allow

# enable writing settings so that backlight can be turned off by setting short timeout value
adb -s "$serial" shell appops set com.immichframe.immichframe WRITE_SETTINGS allow

# grant location permission so the app can display the connected Wi-Fi SSID
adb -s "$serial" shell pm grant com.immichframe.immichframe android.permission.ACCESS_FINE_LOCATION
adb -s "$serial" shell pm grant com.immichframe.immichframe android.permission.ACCESS_COARSE_LOCATION

echo "Successfully installed ImmichFrame for Frameo app on device with Android ${version}, build ID ${buildid}, webview version ${webview_version}"
