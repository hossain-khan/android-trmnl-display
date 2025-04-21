[![Android CI](https://github.com/hossain-khan/android-trmnl-display/actions/workflows/android.yml/badge.svg)](https://github.com/hossain-khan/android-trmnl-display/actions/workflows/android.yml)

# Android - TRMNL Display
A simple app to mirror existing TRMNL's content to your Android device (preferably e-ink display).

## 📜 Preconditions
You must have a **valid** `access-token` to access the [screen content](https://docs.usetrmnl.com/go/private-api/fetch-screen-content) using TRMNL server API.

Here are some of the known ways you can get access to the `access-token`.

1. You must own a [TRMNL](https://usetrmnl.com/) device with "developer edition" add-on purchased
2. You have purchased their [BYOD](https://docs.usetrmnl.com/go/diy/byod)* product. (Not confirmed if you also need to buy "developer edition" add-on)
3. You have self-serve installation of TRMNL service using [BYOS](https://docs.usetrmnl.com/go/diy/byos) (Not confirmed if this works - I plan to try this later)


## How to try
Once released, install the APK on your Android device.

1. Configure the API `access-token` in the app settings
    i. 📝 NOTE: Right now only `https://usetrmnl.com/api` service API is supported, custom service URL will be added later
2. Save token and keep the app on with the TRMNL content/image showing.

### <img src="https://github.com/user-attachments/assets/6f8adaa7-723c-4faa-be61-c0145fd79889" width="25"> Download Release
Check installable APK from Assets in [latest release](https://github.com/hossain-khan/android-trmnl-display/releases).

### Limitations 🚧
* Right now, screen lock using Google's [recommended](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on) **`FLAG_KEEP_SCREEN_ON`** is not working on e-Ink tablet due to strict battery optimization. So, if you plan to keep the screen on idefinitely, you should set that in the device settings.
    * On normal Android tablet or device, screen wake lock should work. However it's not recommended to use it without device being always plugged-in 🔌.

> ℹ️ _I am using 'BOOX GO 10.3 Tablet' that lasts around 5 days with always screen on._


## Application Overview

The TRMNL Display Mirror app serves as a digital display mirror for TRMNL e-ink devices. The app connects to the TRMNL API, fetches display data, and shows it on Android devices.

### Key Features

- [x] Token-based authentication with the TRMNL API
- [x] Automatic periodic image refresh from the server
- [x] Adaptive refresh rate based on server response config
- [x] Manual refresh capabilities
- [x] Image caching for offline viewing
- [x] Refresh history logging for tracking & validation
