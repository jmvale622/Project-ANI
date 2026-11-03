# ANI Android Runtime Comparison

## Recommendation

### Results at a glance

| Measure (current 26-class model) | ONNX Runtime | LiteRT / TensorFlow Lite | Better result |
|---|---:|---:|---|
| Validation top-1 accuracy | 96.24% (3,580 / 3,720) | 96.24% (3,580 / 3,720) | Tie |
| Validation top-5 accuracy | 99.89% (3,716 / 3,720) | 99.89% (3,716 / 3,720) | Tie |
| Top-1 prediction agreement | Same predictions on all 3,750 comparison images | Same predictions on all 3,750 comparison images | Tie |
| Maximum absolute output-score difference | `6.17e-6` | `6.17e-6` | Equivalent outputs |
| Emulator median classification time | 38.60 ms | 77.74 ms | ONNX Runtime |
| Emulator P90 classification time | 40.42 ms | 81.89 ms | ONNX Runtime |
| Emulator initialization time | 795.24 ms | 103.71 ms | LiteRT / TensorFlow Lite |
| Currently used by normal offline app flow | Yes | No | ONNX Runtime |

### Which should you use?

Use **ONNX Runtime** as the app's primary offline inference engine for now.
Accuracy is effectively tied: both exported models produced the same measured
validation metrics and identical top-1 predictions. The practical difference
is speed and integration. In the current Pixel 7 emulator benchmark, ONNX had
about **50% lower median classification latency** (38.60 ms vs. 77.74 ms),
and it is already wired into `OfflineClassifier`.

Choose **LiteRT / TensorFlow Lite** instead if faster model initialization is
more important for your app's startup behavior, or if testing on your target
phone shows better performance or compatibility. In this emulator run, it
initialized in about 104 ms compared with about 795 ms for ONNX. Both formats
are compatible with the app and can run offline.

These timing results come from one emulator, not a range of physical phones.
Benchmark on the intended device before treating the latency difference as
universal. Both model formats are currently shipped in the debug APK, so
selecting one runtime for production and removing the unused asset/runtime
could reduce app size and maintenance.

## What is wired into the app

`OfflineClassifier` calls `OnnxAniClassifier`. `LiteRtAniClassifier` is
implemented and loads the TFLite model asset, but is not currently selected by
the app's normal classification flow. Both assets and runtimes are present in
the debug build.

The current 26-class ONNX and LiteRT/TFLite models accept
`[1, 3, 224, 224]` float input and return `[1, 26]` float output. Both output
orders match the Android labels. The PyTorch model validated at 96.34% top-1
and 99.89% top-5 on 3,720 held-out images. We evaluated both exported runtimes
on those same images using identical RGB, 224x224, normalized NCHW inputs:

| Runtime | Top-1 correct | Top-1 accuracy | Top-5 correct | Top-5 accuracy |
|---|---:|---:|---:|---:|
| ONNX Runtime (CPU) | 3,580 / 3,720 | 96.24% | 3,716 / 3,720 | 99.89% |
| LiteRT/TFLite (CPU, XNNPACK) | 3,580 / 3,720 | 96.24% | 3,716 / 3,720 | 99.89% |

The two runtimes produced the same top-1 class on all 3,750 comparison images:
the 3,720 validation images and 30 independent Sugarcane pest test crops. The
maximum absolute difference between output scores was `6.17e-6`.

At the Android 70% confidence threshold, Rice Pest filtering on validation
accepted 50 images; 47 predictions were exactly correct and one accepted image
was a non-pest class. Per-class correct counts were Rice Bug `19/20`, Rice
Leaf Roller `15/16`, and Rice Stem Borer `13/14`.

Sugarcane Pest filtering accepted 63 of 3,720 validation images, with 49
accepted predictions exactly correct and no accepted non-pest validation
images. Per-class correct counts were Leaf Hopper `12/16`, Root/Base Pest
`13/22`, Shoot Borer `10/17`, and White Sucking Pest `14/20`. On the separate,
pest-only test split, 25 of 30 crops were accepted and 12 of those 25
predictions were exactly correct. The independent test set is small and
contains no non-pest images, so it cannot estimate false-positive rate; these
results are a warning that Sugarcane pest predictions need more labeled data
and field validation before being relied on.

Independent Sugarcane test results by class: Leaf Hopper `4/4` correct,
Root/Base Pest `2/5`, Shoot Borer `3/7`, and White Sucking Pest `3/14`.

**Offline recommendation:** Accuracy does not distinguish these runtimes:
both exported formats produced the same measured accuracy and predictions on
the evaluated data. Both formats are compatible with the Android classifier
interfaces and `[1, 3, 224, 224]` / `[1, 26]` tensor shapes. Keep **ONNX
Runtime** as the production offline path because `OfflineClassifier` already
uses it and both emulator comparisons favored ONNX for median inference time.
The 26-class timing is still emulator-only and does not prove that ONNX is
faster on every phone. LiteRT/TFLite is also a valid compatible alternative;
compare its on-device latency and startup time on the target device before
switching.

## Android emulator timing

Measured on 2026-10-23 using the connected Pixel 7 AVD (Android 17, API 37,
ARM64 emulator). Each run used the app's classifier classes, a deterministic
synthetic 224x224 bitmap, 5 warm-up inferences, and 40 timed inferences. Timed
classification includes shared image preprocessing and prediction filtering;
it excludes image decoding and UI work. This is a runtime timing check, not a
classification-accuracy test.

| Runtime | Run | Constructor time (ms) | Median classification (ms) | P90 classification (ms) |
|---|---:|---:|---:|---:|
| ONNX Runtime | 1 | 1,038.8 | 39.58 | 49.45 |
| TensorFlow Lite | 1 | 38.0 | 76.71 | 84.91 |
| ONNX Runtime | 2 | 1,304.0 | 41.28 | 49.78 |
| TensorFlow Lite | 2 | 41.0 | 81.35 | 95.38 |

Across these runs, ONNX Runtime's median was about `40.43 ms`, versus
`79.03 ms` for TensorFlow Lite (about `48.8%` lower latency in this setup).
Constructor times were also measured but are not directly equivalent to
first-launch user experience; ONNX creates an ONNX session and may copy the
model asset when its cached version is stale. TensorFlow Lite maps its model
asset and creates an interpreter.

The tested settings are the current implementation settings: ONNX Runtime uses
two intra-op threads; TensorFlow Lite uses its default `Interpreter` options.
The emulator and these thread settings are not representative of every
physical device. Real phones may have different CPU, delegate, and thermal
behavior.

### Updated 26-class model check

Measured on 2026-10-26 using the connected Pixel 7 AVD (Android 17, API 37,
ARM64 emulator). The same deterministic synthetic bitmap, 5 warm-up
inferences, 40 timed inferences, and timing boundaries were used:

| Runtime | Run | Constructor time (ms) | Median classification (ms) | P90 classification (ms) |
|---|---:|---:|---:|---:|
| ONNX Runtime | 1 | 795.24 | 38.60 | 40.42 |
| TensorFlow Lite | 1 | 103.71 | 77.74 | 81.89 |

In this run ONNX Runtime's median was `50.4%` lower than TensorFlow Lite's.
This confirms the prior emulator timing trend for the current 26-class model,
but remains an emulator-only comparison and not a claim about all phones.

## Compatibility and limitations

- Both 26-class models, their `[1, 26]` output shapes, held-out accuracy, and
  3,750-image ONNX/LiteRT prediction parity were verified on the host.
  The connected Pixel 7 AVD also ran the updated runtime comparison on the
  current 26-class assets.
- This does not certify compatibility with every Android version or phone.
  The project's configured minimum Android API is 24; the available emulator
  benchmark only covered API 37.
- Rice Bug, Rice Leaf Roller, Rice Stem Borer, and four Sugarcane pest types
  are included as model classes. These are small datasets, and the independent
  Sugarcane pest test accuracy is limited; review class-level results and
  collect more representative field data before relying on these predictions.
- Overall validation accuracy is not a guarantee for a particular field image
  or for arbitrary unrelated images.

## Reproducing the Android timing check

With an Android emulator or USB-debugging-enabled phone connected, run from the
Android project directory:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.dysrupit.ani.utils.RuntimeComparisonTest
```

The test is
[`RuntimeComparisonTest.kt`](../../../AndroidStudioProjects/ANIKotlin/app/src/androidTest/java/com/dysrupit/ani/utils/RuntimeComparisonTest.kt)
in this workspace. It logs per-runtime timings with the `AniRuntimeBenchmark`
tag; retrieve them with:

```sh
adb logcat -d -s AniRuntimeBenchmark:I '*:S'
```

For a final device choice, rerun this test on the actual target phone. If
TensorFlow Lite is not needed after that comparison, removing its model asset,
runtime dependency, and unused classifier would reduce duplicated app payload
and maintenance.
