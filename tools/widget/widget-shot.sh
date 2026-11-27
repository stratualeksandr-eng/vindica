#!/bin/bash
# On the emulator: install the debug build; shots of the widgets in a stand-in home screen, of the live wallpaper's
# picture by night and by day (turned over, in Romanian, under cloud), then the real live wallpaper set on the home screen.
A=app.vindica.diary
APK=android/app/build/outputs/apk/debug/app-debug.apk
OUT=shots/${API:-x}
mkdir -p $OUT
log() { echo "== $(date -u +%T) $*"; }
t() { timeout "$@" || echo "!! timeout or failure: $*"; }
shot() { sleep "$2"; t 30 adb shell screencap -p /sdcard/s.png && t 30 adb pull /sdcard/s.png "$OUT/$1.png" >/dev/null; log "shot $1 $(stat -c %s $OUT/$1.png 2>/dev/null)"; }
host() { t 60 adb shell am start -S -W -n $A/.WidgetHostActivity "$@"; }
sky() { t 60 adb shell am start -S -W -n $A/.SkyTestActivity "$@"; }
settime() { t 20 adb shell "date $1"; }
tapon() { t 30 adb shell uiautomator dump /sdcard/ui.xml >/dev/null; t 30 adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null; XY=$(python3 tools/widget/find.py /tmp/ui.xml "$1"); log "tap $1 at [$XY]"; [ -n "$XY" ] && t 10 adb shell input tap $XY; }

log boot
t 180 adb install -r -g $APK
t 30 adb shell appwidget grantbind --package $A --user 0
t 20 adb shell settings put global auto_time 0
t 20 adb shell settings put global auto_time_zone 0
t 30 adb root
sleep 5
t 60 adb wait-for-device
t 20 adb shell date
t 20 adb shell run-as $A mkdir -p files
for f in hero-ru.json hero-ro.json hero-ru-light.json hero-ru-nobirth.json; do
  t 30 adb shell "run-as $A sh -c 'cat > files/$f'" < tools/widget/$f
done
log ready

# the widgets, once more
settime 112721302026.00
host --es fixture hero-ru.json --es which both
shot 1-widgets-night 8
# the live wallpaper's picture: night with the real weather, then turned over
sky --es fixture hero-ru.json
shot 2-sky-night 10
sky --es fixture hero-ru.json --ez flip true
shot 3-sky-night-back 6
shot 3-sky-night-back-b 3
# by day under cloud, the life total in weeks
settime 112711052026.00
sky --es fixture hero-ru.json --es wx "3,2,0.7" --ei unit 1
shot 4-sky-day-cloud 8
# Romanian, turned over, the total in hours
sky --es fixture hero-ro.json --es wx "61,6,0.9" --ez flip true --ei unit 2
shot 5-sky-day-ro-back 8
# dusk, clear and frosty; the light look at night; no birth date
settime 112716252026.00
sky --es fixture hero-ru.json --es wx "0,-3,0"
shot 6-sky-dusk 8
settime 112723102026.00
sky --es fixture hero-ru-light.json --es wx "45,-1,0.4"
shot 7-sky-night-light 8
sky --es fixture hero-ru-nobirth.json --es wx "2,-4,0.3"
shot 8-sky-nobirth 8
# the place: Buzău by night (the jet on its pylon lit), Buzău by day, a town without a drawing (soft hills)
settime 112721302026.00
sky --es fixture hero-ru.json --es place "45.152,26.817," --es wx "2,3,0.3"
shot 12-sky-buzau-night 8
settime 112711052026.00
sky --es fixture hero-ru.json --es place "45.152,26.817," --es wx "1,8,0.2"
shot 13-sky-buzau-day 8
settime 112716252026.00
sky --es fixture hero-ru.json --es place "45.35,25.55,Sinaia" --es wx "71,-2,0.6"
shot 14-sky-elsewhere 8
sky --ez noplace true --es fixture hero-ru.json --es wx "45,1,0.8"
shot 15-sky-noplace 6
# the phone is in Buzău (a mock fix): the app's own look finds it, in the foreground
settime 112721302026.00
t 20 adb shell settings put secure location_mode 3
t 20 adb emu geo fix 26.8167 45.1517
sleep 3
t 20 adb emu geo fix 26.8167 45.1517
sky --ez noplace true --es fixture hero-ru.json --ez locate true
shot 16-sky-locate 25
t 30 adb shell run-as $A cat shared_prefs/vindica_widget.xml > $OUT/prefs-locate.xml
sky --ez noplace true --es fixture hero-ru.json --es wx "45,1,0.8"
sleep 3
t 60 adb logcat -d > $OUT/logcat-a-full.txt
grep -iE "vindica|AndroidRuntime|Wallpaper|FATAL" $OUT/logcat-a-full.txt | tail -300 > $OUT/logcat-a.txt

# the real live wallpaper: the phone's preview, set it, then the home screen and a tap on the dial; the phone stands
# in Buzău (a mock fix), the wallpaper should find it by itself
settime 112721302026.00
t 20 adb shell am force-stop $A
t 20 adb shell pm grant $A android.permission.ACCESS_COARSE_LOCATION
t 20 adb shell pm grant $A android.permission.ACCESS_BACKGROUND_LOCATION
t 20 adb emu geo fix 26.8167 45.1517
# the launcher's «isn't responding» from boot covers the screen on Android 14: wait it away
tapon "^wait$|подождать"
t 20 adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS
sleep 2
t 30 adb shell am start -a android.service.wallpaper.CHANGE_LIVE_WALLPAPER --ecn android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT $A/$A.SkyWallpaper
shot 9-preview 10
tapon "set wallpaper|set|apply|установить"
sleep 3
tapon "home screen|home and lock screens|home & lock screens"
sleep 3
t 10 adb shell input keyevent KEYCODE_HOME
shot 10-home 8
t 20 adb emu geo fix 26.8167 45.1517
shot 10-home-b 20
t 30 adb shell dumpsys location | grep -iE "last known|vindica|gps|fused|network" | head -40 > $OUT/location.txt
t 30 adb shell run-as $A cat shared_prefs/vindica_widget.xml > $OUT/prefs.xml
W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); WX=${W%x*}; WY=${W#*x}
t 10 adb shell input tap $((WX/2)) $((WY*57/100))
shot 11-home-tap 4
t 60 adb logcat -d > $OUT/logcat-full.txt
grep -iE "vindica|AndroidRuntime|Wallpaper|FATAL" $OUT/logcat-full.txt | tail -400 > $OUT/logcat.txt
log done
true
