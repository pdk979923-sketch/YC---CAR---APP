# YC-CAR-APP

Complete Android Studio project structure for building the YC Car Controller APK with GitHub Actions.

## Build
Open GitHub -> Actions -> Build YC Car APK -> Run workflow.

The generated APK is uploaded as an Actions artifact named `YC-CAR-APP-debug`.

Note: the current Java code provides the controller UI and Bluetooth permission handling. The exact BLE service/characteristic and command bytes for the specific YC car still need to be known before real motor commands can be sent.
