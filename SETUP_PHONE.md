# Setting Up Your Phone for Debugging

## Current Status
✅ ADB found and working
✅ Emulator connected (emulator-5554) - Ready to use!
⚠️  Physical device (R5CY91XF6QP) - Needs authorization

## To Authorize Your Physical Phone:

1. **Check your phone screen** - You should see a popup asking:
   "Allow USB debugging?"
   - Check "Always allow from this computer"
   - Tap "OK" or "Allow"

2. **If you don't see the popup:**
   - Unplug and replug the USB cable
   - Or run: `adb kill-server` then `adb devices` again

3. **Verify connection:**
   ```powershell
   C:\Users\jcdau\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
   ```
   Should show: `R5CY91XF6QP    device` (not "unauthorized")

## Quick Start Options:

### Option 1: Use the Emulator (Easiest)
The emulator is already connected! Just run:
```powershell
.\connect_and_debug.ps1
```
It will automatically use the emulator.

### Option 2: Use Your Physical Phone
1. Authorize the phone (see above)
2. Run: `.\connect_and_debug.ps1`
3. It will detect and use your phone

### Option 3: Quick Debug (Fastest)
```powershell
.\quick_debug.ps1
```
Installs APK and shows only errors in real-time.

## What the Scripts Do:

1. **connect_and_debug.ps1** - Full debugging:
   - Checks device connection
   - Installs APK
   - Monitors logcat with color-coded output
   - Shows crashes in red

2. **quick_debug.ps1** - Quick mode:
   - Fast install
   - Shows only errors and MPDroid messages

3. **capture_crash_log.ps1** - Capture logs:
   - Saves full logcat to file
   - Extracts crash stack traces
   - Useful for sharing crash reports

## Next Steps:

Once your phone is authorized (or using emulator), run:
```powershell
.\connect_and_debug.ps1
```

Then launch the MPDroid app on your device/emulator and watch for any crashes!


