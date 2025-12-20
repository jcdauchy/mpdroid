# Testing Guide for MPDroid

## Quick Testing Options

### Option 1: Manual Testing with Logcat (Recommended)

1. **Connect your Android device** via USB and enable USB debugging, OR start an Android emulator

2. **Install the APK**:
   ```powershell
   # If ADB is in your PATH:
   adb install -r "MPDroid\build\outputs\apk\debug\MPDroid-debug.apk"
   
   # Or use Android Studio: Build > Build Bundle(s) / APK(s) > Build APK(s)
   # Then right-click the APK and select "Run 'app'"
   ```

3. **Monitor crash logs**:
   ```powershell
   # Clear logcat first
   adb logcat -c
   
   # Monitor for crashes and errors
   adb logcat | Select-String -Pattern "AndroidRuntime|FATAL|MPDApplication|Exception|Error" -Context 2,5
   ```

4. **Launch the app** on your device and watch the logcat output for any crashes

### Option 2: Using Android Studio

1. Open the project in Android Studio
2. Connect a device or start an emulator
3. Click "Run" (green play button) or press `Shift+F10`
4. Check the "Logcat" tab at the bottom for any errors
5. Filter by: `package:mine` and `level:error` or `level:fatal`

### Option 3: Check for Common Issues

The fixes we made address these potential crash causes:

✅ **Fixed**: Deprecated `android.preference.PreferenceManager` (removed in API 33+)
✅ **Fixed**: Static initialization order issues in:
   - `LibraryTabsUtil`
   - `PhoneStateReceiver` 
   - `GracenoteCover`

### Option 4: Verify Build Success

The app builds successfully:
- ✅ Debug APK: `MPDroid\build\outputs\apk\debug\MPDroid-debug.apk`
- ✅ Release APK: `MPDroid\build\outputs\apk\release\MPDroid-release.apk`

## What to Look For

If the app still crashes, check logcat for:

1. **NullPointerException** - Should be fixed by our null checks
2. **ClassNotFoundException** - Check for missing dependencies
3. **IllegalStateException** - Check Application initialization order
4. **RuntimeException** - Check for any unhandled exceptions

## Common Crash Patterns Fixed

1. **Static field initialization before Application.onCreate()**
   - Fixed by converting to lazy-loaded methods with null checks

2. **PreferenceManager API removed in API 33+**
   - Fixed by using compatibility method `MPDApplication.getDefaultSharedPreferences()`

3. **Missing null checks**
   - Added null checks in all static initialization code

## Getting Crash Logs

If the app crashes, get the full stack trace:

```powershell
# Get the last crash log
adb logcat -d | Select-String -Pattern "FATAL EXCEPTION" -Context 0,50

# Or save to file
adb logcat -d > crash_log.txt
```

## Next Steps

If crashes persist:
1. Share the full logcat output (especially the stack trace)
2. Note the Android version you're testing on
3. Note what happens when you launch the app (immediate crash, crash after loading, etc.)

