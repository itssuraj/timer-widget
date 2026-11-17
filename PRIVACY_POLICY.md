# Privacy Policy for Timer Widget

**Last updated:** November 17, 2026

Timer Widget ("we", "our", or "the app") is designed with a strict privacy-first philosophy. This Privacy Policy describes how your information is handled when using Timer Widget.

### 1. Information Collection and Storage
* **Zero Personal Data Collection**: We do not collect, transmit, sell, or share any personal identifiable information (PII).
* **Local Device Storage**: All timer configurations, custom presets, and widget states are stored entirely on your local device using Android Jetpack DataStore. No data is ever transmitted to external servers, cloud storage, or third-party analytics providers.

### 2. Permissions Used
Timer Widget requests only the minimum permissions required to provide core timer and widget functionality:
* **Notifications (`POST_NOTIFICATIONS`)**: Used solely to display the active countdown status in the notification shade and to alert you when your timer completes.
* **Foreground Service (`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`)**: Used to keep active timers running accurately with real-time second-by-second countdown updates for your home screen widget without being terminated by system battery optimization.
* **Vibration (`VIBRATE`)**: Used to vibrate the device when a countdown timer reaches zero.
* **Run at Startup (`RECEIVE_BOOT_COMPLETED`)**: Used to restore the home screen widget state after your device restarts.

### 3. Third-Party Services
Timer Widget does not include any third-party SDKs, tracking tools, advertisement networks, or analytics frameworks.

### 4. Children's Privacy
Because Timer Widget does not collect any personal data whatsoever, it is safe for users of all ages, including children.

### 5. Changes to This Policy
If this Privacy Policy is updated in the future, the revised policy will be posted on this page with an updated revision date.

### 6. Contact
If you have any questions or feedback regarding this Privacy Policy, please open an issue in the project's repository.
