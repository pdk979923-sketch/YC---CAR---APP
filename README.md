# YC Car Controller

Android BLE controller for the YC_CAR_DEMO protocol.

Controls:
- Forward: AA 00 02 00 00 00 00 42 00 02
- Reverse: AA 00 02 00 00 00 00 41 00 02
- Left: AA 00 02 00 00 00 00 44 00 02
- Right: AA 00 02 00 00 00 00 48 00 02
- Stop: AA 00 02 00 00 00 00 40 00 02

Packets are sent every 100 ms while a direction is held, with a stop packet on release.
The controller looks for the FFF2 writable characteristic and falls back to the first writable BLE characteristic.
