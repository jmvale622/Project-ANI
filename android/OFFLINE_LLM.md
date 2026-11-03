# Offline recommendation model

ANI's offline scan can generate crop-care advice on the phone with LiteRT-LM.
Image classification and the existing built-in recommendations continue to
work when the optional language model is not installed or cannot initialize.

## Install the model

Open **Profile → Offline AI recommendations** while connected to the internet
and choose **Download**. The app downloads the 331 MiB model once, verifies its
SHA-256 checksum, and stores it in the app's non-backed-up private files
directory. The model is not bundled into the APK. Keep the app open during the
download; an interrupted download can be resumed from Settings.

After installation, offline scans run the language model locally. Crop labels
and confidence are not sent to a service for this generation. The app tries
the LiteRT-LM GPU backend first and falls back to CPU if GPU initialization
fails. A separate connection is still needed for online server features and
the first model download.

## Model and license

- Runtime: Google LiteRT-LM Android, pinned to `0.16.1` for compatibility with
  the app's Kotlin toolchain.
- Model: [Qwen3-0.6B-int4](https://huggingface.co/litert-community/Qwen3-0.6B-int4),
  non-thinking Q4 block-32 variant with a 1,280-token KV cache.
- Model revision: `256f33043931947c173d7f3cc1d84ee7a8538cc2`.
- Model SHA-256:
  `2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139`.
- License: Apache-2.0 (see the model card and Qwen license notices).

The generated action is validated and the suggested-product field remains a
fixed reminder to consult a local agricultural officer. If the model is
missing, returns malformed output, or fails to start, ANI uses its existing
built-in offline recommendation. LLM output is advisory and must not replace
field diagnosis or locally approved agricultural guidance.

LiteRT-LM's Android package currently includes native runtime binaries for
64-bit ARM and x86_64 devices. Other Android ABIs continue to use ANI's
existing on-device classifier and built-in recommendation fallback. Low-RAM
devices also skip the optional language model to avoid excessive memory use.
