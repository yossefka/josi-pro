
package org.tensorflow.lite.examples.classification;

import android.annotation.SuppressLint;
import android.app.Activity;//An activity is a single, focused thing that the user can do
import android.app.AlertDialog;//A subclass of Dialog that can display one, two or three buttons
import android.app.Dialog;//Activities provide a facility to manage the creation, saving and restoring of dialogs
import android.app.DialogFragment;//A fragment that displays a dialog window, floating on top of its activity's window
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;//all device configuration information that can impact the resources the application retrieves
import android.graphics.ImageFormat;
import android.graphics.Matrix;//The Matrix class holds a 3x3 matrix for transforming coordinates
import android.graphics.RectF;//RectF holds four float coordinates for a rectangle
import android.graphics.SurfaceTexture;//Captures frames from an image stream
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;//configured capture session for a CameraDevice
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;//An immutable package of settings and outputs needed to capture a single image from the camera device
import android.hardware.camera2.CaptureResult;//The subset of the results of a single image capture from the image sensor.
import android.hardware.camera2.TotalCaptureResult;//The total assembled results of a single image capture from the image sensor
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Size;
import android.util.SparseIntArray;//SparseIntArrays map integers to integers
import android.view.LayoutInflater;//Instantiates a layout XML file into its corresponding View objects.
import android.view.Surface;
import android.view.TextureView;//A TextureView can be used to display a content stream.
import android.view.View;
import android.view.ViewGroup;//A ViewGroup is a special view that can contain other views (called children.)
import android.widget.Toast;

import org.tensorflow.lite.examples.classification.customview.AutoFitTextureView;
import org.tensorflow.lite.examples.classification.env.Logger;

import java.util.ArrayList;//Resizable-array implementation of the List interface
import java.util.Arrays;//class contains various methods for manipulating arrays (such as sorting and searching).
import java.util.Collections;//return a new collection backed by a specified collection, and a few other odds and ends.
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

                @SuppressLint("ValidFragment")
                public class CameraConnectionFragment extends Fragment {
                  private static final Logger LOGGER = new Logger();
                  /**The camera preview size will be chosen to be the smallest frame by pixel size capable of
                   * containing a DESIRED_SIZE x DESIRED_SIZE square.*/
                  private static final int MINIMUM_PREVIEW_SIZE = 320;
                  /** Conversion from screen rotation to JPEG orientation. */
                  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

                  private static final String FRAGMENT_DIALOG = "dialog";

                  static {
                    ORIENTATIONS.append(Surface.ROTATION_0, 90);
                    ORIENTATIONS.append(Surface.ROTATION_90, 0);
                    ORIENTATIONS.append(Surface.ROTATION_180, 270);
                    ORIENTATIONS.append(Surface.ROTATION_270, 180);
                  }

                    /** A {@link Semaphore} to prevent the app from exiting before closing the camera. */
              private final Semaphore cameraOpenCloseLock = new Semaphore(1);
                    /** A {@link OnImageAvailableListener} to receive frames as they are available. */
              private final OnImageAvailableListener imageListener;
                    /** The input size in pixels desired by TensorFlow (width and height of a square bitmap). */
              private final Size inputSize;
                    /** The layout identifier to inflate for this Fragment. */
              private final int layout;
              private final ConnectionCallback cameraConnectionCallback;
               //A callback object for tracking the progress of a CaptureRequest submitted to the camera device.ccb
              private final CameraCaptureSession.CaptureCallback captureCallback =
                      new CameraCaptureSession.CaptureCallback() {

          public void onCaptureProgressed(
                    final CameraCaptureSession session,//CameraCaptureSession is created by providing a set of target output surfaces to createCaptureSession
                    final CaptureRequest request,
                    final CaptureResult partialResult) {}//CaptureResults are produced by a CameraDevice after processing a CaptureRequest

          public void onCaptureCompleted(
                    final CameraCaptureSession session,
                    final CaptureRequest request,
                    //TotalCaptureResult is produced by a CameraDevice after processing a CaptureRequest.tcr
                    final TotalCaptureResult result) {}
      };
              /** ID of the current {@link CameraDevice}. */
           private String cameraId;
              /** An {@link AutoFitTextureView} for camera preview. */
           private AutoFitTextureView textureView;
              /** A {@link CameraCaptureSession } for camera preview. */
           private CameraCaptureSession captureSession;
              /** A reference to the opened {@link CameraDevice}. */
           private CameraDevice cameraDevice;
              /** The rotation in degrees of the camera sensor from the display. */
           private Integer sensorOrientation;
              /** The {@link Size} of camera preview. */
           private Size previewSize;
              /** An additional thread for running tasks that shouldn't block the UI. */
           private HandlerThread backgroundThread;
              /** A {@link Handler} for running tasks in the background. */
           private Handler backgroundHandler;
              /** {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a {@link
               * TextureView}.*/
         private final TextureView.SurfaceTextureListener surfaceTextureListener =
                 new TextureView.SurfaceTextureListener() {
               public void  onSurfaceTextureAvailable(
                      final SurfaceTexture texture, final int width, final int height) {
                            openCamera(width, height);
            }
               public void  onSurfaceTextureSizeChanged(
                      final SurfaceTexture texture, final int width, final int height) {
                            configureTransform(width, height);
            }

               public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                      return true;
            }
               public void onSurfaceTextureUpdated(final SurfaceTexture texture) {}
          };
                    /** An {@link ImageReader} that handles preview frame capture. */
           private ImageReader previewReader;
               /** {@link CaptureRequest.Builder} for the camera preview */
           private CaptureRequest.Builder previewRequestBuilder;
                /** {@link CaptureRequest} generated by {@link #previewRequestBuilder} */
           private CaptureRequest previewRequest;
                /** {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state. */
           private final CameraDevice.StateCallback stateCallback =
                   new   CameraDevice.StateCallback() {

                   public void onOpened(final CameraDevice cd) {
                     // This method is called when the camera is opened.  We start camera preview here.
                               cameraOpenCloseLock.release();
                               cameraDevice = cd;
                               createCameraPreviewSession();
                }

                    public void onDisconnected(final CameraDevice cd) {
                               cameraOpenCloseLock.release();
                               cd.close();
                               cameraDevice = null;
                }

                    public void onError(final CameraDevice cd, final int error) {
                                cameraOpenCloseLock.release();
                                cd.close();
                                cameraDevice = null;
                           final Activity activity = getActivity();
                      if (null != activity) {
                                 activity.finish();
                  }
                }
              };
               //Callback for Activities to use to initialize their data once the selected preview size is known.ccb
              private CameraConnectionFragment(
                  final ConnectionCallback connectionCallback,
                  final OnImageAvailableListener imageListener,
                  final int layout,
                  final Size inputSize) {
                this.cameraConnectionCallback = connectionCallback;
                this.imageListener = imageListener;
                this.layout = layout;
                this.inputSize = inputSize;
              }

                  /**Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
                   * width and height are at least as large as the minimum of both, or an exact match if possible.
                   * @param choices The list of sizes that the camera supports for the intended output class
                   * @param width The minimum desired width
                   * @param height The minimum desired height
                   * @return The optimal {@code Size}, or an arbitrary one if none were big enough*/
              protected static Size chooseOptimalSize(final Size[] choices, final int width, final int height) {
                        final int minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE);
                        final Size desiredSize = new Size(width, height);
                     // Collect the supported resolutions that are at least as big as the preview Surface
               boolean exactSizeFound = false;
               final List<Size> bigEnough = new ArrayList<Size>();//Resizable-array implementation of the List interface
               final List<Size> tooSmall = new ArrayList<Size>();
                for (final Size option : choices) {
                  if (option.equals(desiredSize)) {
                    // Set the size but don't return yet so that remaining sizes will still be logged.
                    exactSizeFound = true;
                  }

                if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
                  bigEnough.add(option);
                } else {
                  tooSmall.add(option);//Appends the specified element to the end of this list (optional operation).
                }
                }
                       //Returns a string containing the tokens joined by delimiters
                    LOGGER.i("Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize);
                    LOGGER.i("Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
                    LOGGER.i("Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]");

                if (exactSizeFound) {
                  LOGGER.i("Exact size match found.");
                  return desiredSize;
                }

                if (bigEnough.size() > 0) {
                  final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
                  LOGGER.i("Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
                  return chosenSize;
                } else {
                  LOGGER.e("Couldn't find any suitable preview size");
                  return choices[0];
                }
              }

              public static CameraConnectionFragment newInstance(
                     final ConnectionCallback callback,
                     final OnImageAvailableListener imageListener,
                     final int layout,
                     final Size inputSize) {
                return new CameraConnectionFragment(callback, imageListener, layout, inputSize);
              }
                      /*** Shows a {@link Toast} on the UI thread.
                       @param text The message to show*/
              private void showToast(final String text) {
                      final Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                          Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                        }
                      });
                }
              }
              //A ViewGroup is a special view that can contain other views (called children.)
              public View onCreateView(
                  final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
                  return inflater.inflate(layout, container, false);//Inflate a new view hierarchy from the specified xml resource
              }
              public void onViewCreated(final View view, final Bundle savedInstanceState) {
                     textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
              }
              //Called when the fragment's activity has been created and this fragment's view hierarchy instantiated
              public void onActivityCreated(final Bundle savedInstanceState) {
                super.onActivityCreated(savedInstanceState);
              }

              public void onResume() {
                super.onResume();
                startBackgroundThread();

                // When the screen is turned off and turned back on, the SurfaceTexture is already
                // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
                // a camera and start preview from here (otherwise, we wait until the surface is ready in
                // the SurfaceTextureListener).
                if (textureView.isAvailable()) {
                  openCamera(textureView.getWidth(), textureView.getHeight());
                } else {
                  textureView.setSurfaceTextureListener(surfaceTextureListener);
                  //Sets the SurfaceTextureListener used to listen to surface texture events.
                }
              }
              public void onPause() {
                     closeCamera();
                     stopBackgroundThread();
                     super.onPause();
              }

              public void setCamera(String cameraId) {
                this.cameraId = cameraId;
              }

                 /** Sets up member variables related to camera. */
              private void setUpCameraOutputs() {
                      final Activity activity = getActivity();
                      final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
              try {
                   final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                   final StreamConfigurationMap map =
                         characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                         sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                previewSize =
                    chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        inputSize.getWidth(),
                        inputSize.getHeight());
                 // We fit the aspect ratio of TextureView to the size of preview we picked.
                  //Return the current configuration that is in effect for this resource object
                 final int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {//Constant for orientation, value corresponding to the land resource qualifier.OL
                  textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                } else {
                  textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                }
              } catch (final CameraAccessException e) {
                LOGGER.e(e, "Exception!");
              } catch (final NullPointerException e) {
                      // Currently an NPE is thrown when the Camera2API is used but not supported on the
                      // device this code runs.
                      // reuse throughout app.
                 ErrorDialog.newInstance(getString(R.string.camera_error))
                 .show(getChildFragmentManager(), FRAGMENT_DIALOG);//Return a private FragmentManager for placing and managing Fragments inside of this Fragment.gcfm
                   throw new RuntimeException(getString(R.string.camera_error));
             }

                cameraConnectionCallback.onPreviewSizeChosen(previewSize, sensorOrientation);
              }

                      /** Opens the camera specified by {@link CameraConnectionFragment#cameraId}. */
                      private void openCamera(final int width, final int height) {
                        setUpCameraOutputs();
                        configureTransform(width, height);
                        final Activity activity = getActivity();
                        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
                        try {
                          if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                            throw new RuntimeException("Time out waiting to lock camera opening.");
                          }
                          manager.openCamera(cameraId, stateCallback, backgroundHandler);
                        } catch (final CameraAccessException e) {
                          LOGGER.e(e, "Exception!");
                        } catch (final InterruptedException e) {
                          throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
                        }
                      }
              /** Closes the current {@link CameraDevice}. */
              private void closeCamera() {
                try {
                  cameraOpenCloseLock.acquire();
                  if (null != captureSession) {
                    captureSession.close();
                    captureSession = null;
                  }
                  if (null != cameraDevice) {
                    cameraDevice.close();
                    cameraDevice = null;
                  }
                  if (null != previewReader) {
                    previewReader.close();
                    previewReader = null;
                  }
                } catch (final InterruptedException e) {
                  throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
                } finally {
                  cameraOpenCloseLock.release();
                }
              }

              /** Starts a background thread and its {@link Handler}. */
              private void startBackgroundThread() {
                backgroundThread = new HandlerThread("ImageListener");
                backgroundThread.start();
                backgroundHandler = new Handler(backgroundThread.getLooper());
              }
              /** Stops the background thread and its {@link Handler}. */
              private void stopBackgroundThread() {
                backgroundThread.quitSafely();
                try {
                  backgroundThread.join();
                  backgroundThread = null;
                  backgroundHandler = null;
                } catch (final InterruptedException e) {
                  LOGGER.e(e, "Exception!");
                }
              }

              /** Creates a new {@link CameraCaptureSession} for camera preview. */
              private void createCameraPreviewSession() {
                try {
                  final SurfaceTexture texture = textureView.getSurfaceTexture();
                  assert texture != null;
                  // We configure the size of default buffer to be the size of camera preview we want.
                  texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                  // This is the output Surface we need to start preview.
                  final Surface surface = new Surface(texture);
                  // We set up a CaptureRequest.Builder with the output Surface.
                  previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                  previewRequestBuilder.addTarget(surface);
                  LOGGER.i("Opening camera preview: " + previewSize.getWidth() + "x" + previewSize.getHeight());

              // Create the reader for the preview frames.
              previewReader =
                  ImageReader.newInstance(
                      previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);//This format is a generic YCbCr format, capable of describing any 4:2:0 chroma-subsampled planar or
                // semiplanar buffer (but not fully interleaved), with 8 bits per color sample.
              previewReader.setOnImageAvailableListener(imageListener, backgroundHandler);
              previewRequestBuilder.addTarget(previewReader.getSurface());
              // Here, we create a CameraCaptureSession for camera preview.
              cameraDevice.createCaptureSession(
                  Arrays.asList(surface, previewReader.getSurface()),//Returns a fixed-size list backed by the specified array
                  new CameraCaptureSession.StateCallback() {

            public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
              // The camera is already closed
              if (null == cameraDevice) {
                return;
              }
              // When the session is ready, we start displaying the preview.
              captureSession = cameraCaptureSession;
              try {
                // Auto focus should be continuous for camera preview.
                previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,//Whether auto-focus (AF) is currently enabled, and what mode it is set to.
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // Flash is automatically enabled when necessary.
                previewRequestBuilder.set(
                        //The desired mode for the camera device's auto-exposure routine.ae
                    CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                // Finally, we start displaying the camera preview.
                previewRequest = previewRequestBuilder.build();
                captureSession.setRepeatingRequest(
                    previewRequest, captureCallback, backgroundHandler);//Request endlessly repeating capture of images by this capture session
                  //With this method, the camera device will continually capture images using the settings in the provided CaptureRequest, at the maximum rate possible.
              } catch (final CameraAccessException e) {
                LOGGER.e(e, "Exception!");
              }
            }

            public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
              showToast("Failed");
            }
          },
          null);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    }
  }

                  /** Configures the necessary {@link Matrix} transformation to `mTextureView`. This method should be
                   * called after the camera preview size is determined in setUpCameraOutputs and also the size of
                   * `mTextureView` is fixed.
                   * @param viewWidth The width of `mTextureView`
                   * @param viewHeight The height of `mTextureView`*/
                  private void configureTransform(final int viewWidth, final int viewHeight) {
                    final Activity activity = getActivity();
                    if (null == textureView || null == previewSize || null == activity) {
                      return;
                    }
                    final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                    final Matrix matrix = new Matrix();
                    //RectF holds four float coordinates for a rectangle. The rectangle is represented by
                    // the coordinates of its 4 edges (left, top, right, bottom).
                    final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
                    final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
                    final float centerX = viewRect.centerX();
                    final float centerY = viewRect.centerY();
                    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                        //Offset the rectangle by adding dx to its left and right coordinates, and adding dy to its top and bottom coordinates
                      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                      //Set the matrix to the scale and translate values that map the source rectangle to the destination rectangle,
                        // returning true if the the result can be represented
                      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                      final float scale =
                  Math.max((float) viewHeight / previewSize.getHeight(),
                            (float) viewWidth / previewSize.getWidth());
              matrix.postScale(scale, scale, centerX, centerY);//Postconcats the matrix with the specified scale.
              matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            } else if (Surface.ROTATION_180 == rotation) {
              matrix.postRotate(180, centerX, centerY);
            }
            textureView.setTransform(matrix);
          }

          /** Callback for Activities to use to initialize their data once the selected preview size is known.*/
          public interface ConnectionCallback {
            void onPreviewSizeChosen(Size size, int cameraRotation);
          }
          /** Compares two {@code Size}s based on their areas.
           * comparison function, which imposes a total ordering on some collection of objects*/
          static class CompareSizesByArea implements Comparator<Size> {
            public int compare(final Size lhs, final Size rhs) {
              // We cast here to ensure the multiplications won't overflow
              return Long.signum(
                  (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
            }
          }
              /** Shows an error message dialog. */
              //A fragment that displays a dialog window, floating on top of its activity's window.df
              public static class ErrorDialog extends DialogFragment {
                private static final String ARG_MESSAGE = "message";
                public static ErrorDialog newInstance(final String message) {
                  final ErrorDialog dialog = new ErrorDialog();
                  final Bundle args = new Bundle();
                  args.putString(ARG_MESSAGE, message);
                  dialog.setArguments(args);
                  return dialog;
                }

        //Activities provide a facility to manage the creation, saving and restoring of dialogs.
        //A subclass of Dialog that can display one, two or three buttons
        //Callback for creating dialogs that are managed (saved and restored) for you by the activity.oncd
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
          final Activity activity = getActivity();
          return new AlertDialog.Builder(activity)//The AlertDialog class takes care of automatically setting
              .setMessage(getArguments().getString(ARG_MESSAGE))//Return the arguments supplied to setArguments.ga
                  //Interface used to allow the creator of a dialog to run some code when an item on the dialog is clicked
              .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialogInterface, final int i) {
                      activity.finish();
                    }
                  })
              .create();
        }
      }
    }