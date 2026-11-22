# GhostDrop Android prototype
Offline Nearby Connections peer-to-peer one-shot message and file transfer.

Build automatically with GitHub Actions; download the debug APK from the successful workflow run's artifacts.

Requires Android 8+ devices with Google Play services. Run on two phones, enable Bluetooth and Wi-Fi, grant permission, choose receiver on one and sender on other, compare authentication digits and approve on both. One transfer per connection.

**Security:** Nearby Connections encrypts transport; this prototype does not add separate end-to-end encryption. Authentication digits must match to mitigate impostors. Received files are saved in app-specific external storage. Not production tested. This does not form a guaranteed standalone Wi-Fi Direct network.
