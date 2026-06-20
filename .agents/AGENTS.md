# Rules
- Compilation and fixing errors is allowed in the Termux environment.
- After compiling an APK, move or copy the output APK to the shared downloads directory at `~/storage/downloads/` so the user can easily install it on the device.
- Do NOT use the `flutter` CLI for building/compiling on Termux. Instead, follow the native Gradle build workflow described in the project-scoped skill `compile_native_android`.
