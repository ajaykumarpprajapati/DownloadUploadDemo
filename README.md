# DownloadUploadDemo

An Android sample showing how to run **long-running, user-initiated downloads and uploads** that keep going when the user leaves the app.

- **Android 14+ (API 34):** a *user-initiated data transfer (UIDT)* job via `JobScheduler`.
- **Android 13 and below:** a foreground service of type `dataSync`.

Both paths share the same transfer code, so the behavior is identical.

## Features

- Download any URL, with **resume** after an interruption (HTTP `Range`).
- Upload any file you pick as a **streamed multipart POST**, so large files are not loaded into memory.
- Ongoing notification with a progress bar and, on the foreground-service path, a **Cancel** action.
- Live progress and a Cancel button in the app.
- Handles the Android 15 `dataSync` time limit (`onTimeout`).

## How it works

| Android version | Mechanism | Class |
|---|---|---|
| 14+ | User-initiated data transfer job | `TransferJobService` |
| 13 and below | Foreground service (`dataSync`) | `TransferService` |

| File | Role |
|---|---|
| `MainActivity.kt` | UI. Picks the mechanism by API level and starts the transfer from a user tap. |
| `TransferJobService.kt` | UIDT job. Attaches the required notification, runs the work, retries on failure. |
| `TransferService.kt` | Foreground service fallback with a Cancel action. |
| `TransferWork.kt` | Shared job logic: runs the transfer, throttles notification updates, reports the result. |
| `TransferEngine.kt` | Plain HTTP download (with resume) and multipart upload. |
| `Notifs.kt` | Notification channel, progress and completion notifications. |
| `TransferState.kt` | Small in-process hub that sends progress to the open activity. |

### Things worth knowing

- A UIDT job **must be scheduled while the app is visible**, right after a user action, which is why transfers start from button handlers.
- Scheduling a job with a network constraint requires the `ACCESS_NETWORK_STATE` permission.
- A UIDT job must have a notification attached (`setNotification`). The system also lists it in the Task Manager with a Stop button.
- If the system stops a job or a transfer fails, the job reschedules itself. A download resumes from the partial file. An upload restarts from the beginning.
- If the target file already exists and is complete, the server answers the Range request with `416` and the app treats the download as done. Delete the file to download it again.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Network access |
| `ACCESS_NETWORK_STATE` | Required to schedule jobs with a network constraint |
| `POST_NOTIFICATIONS` | Show the progress notification (Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Foreground service path (Android 13 and below) |
| `RUN_USER_INITIATED_JOBS` | UIDT jobs (Android 14+) |

## Requirements

- Android Studio (recent version) or JDK 17+ with the Android SDK
- minSdk 24, targetSdk and compileSdk 35
- Gradle 8.13 (included through the wrapper), Android Gradle Plugin 8.7.3, Kotlin 2.0.21

## Build and run

1. Open the project in Android Studio and run it on a device or emulator, or from the command line:

   ```
   ./gradlew installDebug
   ```

   On Windows use `gradlew.bat installDebug`.

2. Tap **Download** to fetch the default test file. Downloaded files go to the app's external `Download` folder.
3. Tap **Upload file…**, pick a file, and it is sent to the upload URL.
4. Press Home. The transfer keeps running and the notification shows its progress.

The default download URL is a 100 MB test file from OVH, and the default upload URL is `https://httpbin.org/post`. Both are editable in the app.

## Notes

This is a demo. A production app should add authentication, certificate and error handling suited to its server, storage through `MediaStore` or the Storage Access Framework for user-visible files, and a persistent queue if it needs more than one transfer at a time.
