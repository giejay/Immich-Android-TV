# Immich Android TV

Immich is a self hosted backup solution for photos and videos. Current features include:

- Upload and view videos and photos
- Auto backup when the app is opened
- Selective album(s) for backup
- Multi-user support
- Album and Shared albums

More info here: https://github.com/immich-app/immich

This Android TV app will allow you to view those uploaded photos and videos. Current features
include:

| Features                                                                       | Status       |
|:-------------------------------------------------------------------------------|--------------|
| Sign in by phone (https://github.com/giejay/Immich-Android-TV-Authentication)  | Done         |
| Sign in by entering API key                                                    | Done         |
| Demo environment                                                               | Done         |
| Album fetching + Lazy loading                                                  | Done         |
| Showing the photos inside an album                                             | Done         |
| Slideshow of the photos and videos with a configured interval                  | Done         |
| Setting the app as the screensaver                                             | Done         |
| Setting the albums to show in the screensaver                                  | Done         |
| Configure the interval of the screensaver                                      | Done         |
| Add generic sorting of albums and photos                                       | Done         |
| Add sorting for specific album (select last item in row and press right again) | Done         |
| Showing the 4K thumbnail instead of the full image to speed up loading         | Done         |
| Showing the EXIF data and improving the slideshow view                         | Design phase |
| Configure whether to play sound with videos                                    | Not started  |
| Casting capabilities                                                           | Not started  |
| Searching in and for albums                                                    | Not started  |
| Dependency injection with Hilt/Dagger                                          | Not started  |

## Support the project

You can support the project in several ways. The first one is by creating nice descriptive bug
reports if you find any: https://github.com/giejay/Immich-Android-TV/issues/new/choose.
<br><br>Even better is creating a PR: https://github.com/giejay/Immich-Android-TV/pulls.

### Build steps
1. Clone project with `git clone --recurse git@github.com:giejay/Immich-Android-TV.git`
2. Create an account at firebase and create a google-services.json file
3. copy app/src/strings_other.xml.example to app/src/main/res/values/strings_other.xml and modify the address and API keys for your demo server.
4. Build apk with `./gradlew assembleRelease`


 <br><br>
Lastly, if you feel this Android TV app is a useful addition to the already great Immich app, you
might consider buying me a coffee or a beer:

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/giejay)

## FAQ
#### I'n not able to set the app as a screensaver
  1. Enable development mode on the device (click the build number or "Android TV OS Build" 7 times in the System->About settings).
2. Go to System -> Developer Options and enable USB Debugging.
3. If you don't have ADB installed on your PC, follow these instructions: https://www.xda-developers.com/install-adb-windows-macos-linux/
4. After downloading/installing ADB on the PC, connect to the device using it's IP: adb connect 192.168.xx.xx.
5. Once you are connected, execute the following command: 'adb shell settings put secure screensaver_components nl.giejay.android.tv.immich/.playback.ScreenSaverService'
6. Done!
