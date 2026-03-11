# Consumer ProGuard rules for :disastermesh-ai
# These rules are applied to apps that consume this library.

# Keep TFLite model classes
-keep class org.tensorflow.lite.** { *; }

# Keep Vosk classes
-keep class org.vosk.** { *; }
