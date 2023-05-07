
package org.tensorflow.lite.examples.classification;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import org.tensorflow.lite.examples.classification.env.BorderedText;
import org.tensorflow.lite.examples.classification.env.Logger;
import org.tensorflow.lite.examples.classification.tflite.Classifier;
import org.tensorflow.lite.examples.classification.tflite.Classifier.Device;
import org.tensorflow.lite.examples.classification.tflite.Classifier.Model;

import java.io.IOException;
import java.util.List;

            public class ClassifierActivity extends CameraActivity implements OnImageAvailableListener {
              private static final Logger LOGGER = new Logger();
              private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
              private static final float TEXT_SIZE_DIP = 10;
              private Bitmap rgbFrameBitmap = null;
              private long lastProcessingTimeMs;
              private Integer sensorOrientation;
              public Classifier classifier;
              private BorderedText borderedText;
              /** Input image size of the model along x axis. */
              private int imageSizeX;
              /** Input image size of the model along y axis. */
              private int imageSizeY;

              protected int getLayoutId() {
                return R.layout.camera_connection_fragment;
              }
              protected Size getDesiredPreviewFrameSize() {
                return DESIRED_PREVIEW_SIZE;
              }

        public void onPreviewSizeChosen(final Size size, final int rotation) {
          final float textSizePx =
              TypedValue.applyDimension(
                  TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
          borderedText = new BorderedText(textSizePx);
          borderedText.setTypeface(Typeface.MONOSPACE);

          recreateClassifier(getModel(), getDevice(), getNumThreads());
          if (classifier == null) {
            LOGGER.e("No classifier on preview!");
            return;
          }
          previewWidth = size.getWidth();
          previewHeight = size.getHeight();

          sensorOrientation = rotation - getScreenOrientation();
          LOGGER.i("Camera orientation relative to screen : %d", sensorOrientation);

          LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
          rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        }

          protected void processImage() {
            rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
            final int cropSize = Math.min(previewWidth, previewHeight);
            runInBackground(
                new Runnable() {
                  @Override
                  public void run() {
                    if (classifier != null) {
                      final long startTime = SystemClock.uptimeMillis();
                      final List<Classifier.Recognition> results =
                          classifier.recognizeImage(rgbFrameBitmap, sensorOrientation);
                      lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                      LOGGER.v("Detect: %s", results);

                    runOnUiThread(
                      new Runnable() {
                        public void run() {
                          showResultsInBottomSheet(results);
                          showFrameInfo(previewWidth + "x" + previewHeight);
                          showCropInfo(imageSizeX + "x" + imageSizeY);
                          showCameraResolution(cropSize + "x" + cropSize);
                          showRotationInfo(String.valueOf(sensorOrientation));
                          showInference(lastProcessingTimeMs + "ms");
                        }
                      });
                }
            readyForNextImage();
          }
        });
  }

              protected void onInferenceConfigurationChanged() {
                if (rgbFrameBitmap == null) {
                  // Defer creation until we're getting camera frames.
                  return;
                }
                final Device device = getDevice();
                final Model model = getModel();
                final int numThreads = getNumThreads();
                runInBackground(() -> recreateClassifier(model, device, numThreads));
              }

              private void recreateClassifier(Model model, Device device, int numThreads) {
                if (classifier != null) {
                  LOGGER.d("Closing classifier.");
                  classifier.close();
                  classifier = null;
                }
                if (device == Device.GPU && model == Model.QUANTIZED) {
                  LOGGER.d("Not creating classifier: GPU doesn't support quantized models.");
                  runOnUiThread(
                      () -> {
                        Toast.makeText(this, "GPU does not yet supported quantized models.", Toast.LENGTH_LONG)
                            .show();
                      });
                  return;
                }
                try {
                  LOGGER.d(
                      "Creating classifier (model=%s, device=%s, numThreads=%d)", model, device, numThreads);
                  classifier = Classifier.create(this, model, device, numThreads);
                } catch (IOException e) {
                  LOGGER.e(e, "Failed to create classifier.");
                }

            // Updates the input image size.
            imageSizeX = classifier.getImageSizeX();
            imageSizeY = classifier.getImageSizeY();
          }
        }
