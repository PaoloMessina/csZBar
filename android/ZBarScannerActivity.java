package org.cloudsky.cordovaPlugins;

import java.io.IOException;
import java.lang.RuntimeException;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.ShapeDrawable;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.AutoFocusCallback;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;
import net.sourceforge.zbar.Config;

public class ZBarScannerActivity extends Activity
implements SurfaceHolder.Callback {

    // Config ----------------------------------------------------------

    private static int autoFocusInterval = 500; // Interval between AFcallback and next AF attempt.

    // Public Constants ------------------------------------------------

    public static final String EXTRA_QRVALUE = "qrValue";
    public static final String EXTRA_PARAMS = "params";
    public static final int RESULT_ERROR = RESULT_FIRST_USER + 1;

    // State -----------------------------------------------------------

    private Camera camera;
    private Handler autoFocusHandler;
    private SurfaceView scannerSurface;
    private SurfaceHolder holder;
    private ImageScanner scanner;
    private int surfW, surfH;

    // Customisable stuff
    String whichCamera;
    String flashMode;

    /* START - ALMAVIVA */
    RelativeLayout relativeLayout;
    RelativeLayout line;
    View parent;
    int width;
    int height;
    boolean drawSight = false;
    /* END - ALMAVIVA */

    // For retrieving R.* resources, from the actual app package
    // (we can't use actual.application.package.R.* in our code as we
    // don't know the applciation package name when writing this plugin).
    private String package_name;
    private Resources resources;

    // Static initialisers (class) -------------------------------------

    static {
        // Needed by ZBar??
        System.loadLibrary("iconv");
    }

    // Activity Lifecycle ----------------------------------------------

    @Override
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Get parameters from JS
        Intent startIntent = getIntent();
        String paramStr = startIntent.getStringExtra(EXTRA_PARAMS);
        JSONObject params;
        try { params = new JSONObject(paramStr); }
        catch (JSONException e) { params = new JSONObject(); }
        String textTitle = params.optString("text_title");
        String textInstructions = params.optString("text_instructions");
        whichCamera = params.optString("camera");
        flashMode = params.optString("flash");

        // Initiate instance variables
        autoFocusHandler = new Handler();
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);

        drawSight = params.optString("drawSight") != null ? Boolean.valueOf(params.optString("drawSight").toLowerCase()) : true;

        // Set content view
        setContentView(getResourceId("layout/cszbarscanner"));


        // Create preview SurfaceView
        scannerSurface = new SurfaceView (this) {
            @Override
            public void onSizeChanged (int w, int h, int oldW, int oldH) {
                surfW = w;
                surfH = h;
                matchSurfaceToPreviewRatio();
            }
        };
        scannerSurface.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ));
        scannerSurface.getHolder().addCallback(this);

        // Add preview SurfaceView to the screen
        ((FrameLayout) findViewById(getResourceId("id/csZbarScannerView"))).addView(scannerSurface);
		
		/* START - ALMAVIVA */
        // Creating a new RelativeLayout
        if(drawSight){
	        relativeLayout = new RelativeLayout(this);
	        line = new RelativeLayout(this);
	
	        // Defining the RelativeLayout layout parameters.
	        // In this case I want to fill its parent
	        parent = ((FrameLayout) findViewById(getResourceId("id/csZbarScannerView")));
	        
	        parent.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
	
	            @Override
	            public void onGlobalLayout() {
	                // Ensure you call it only once :
	            	parent.getViewTreeObserver().removeGlobalOnLayoutListener(this);
	
	            	width = parent.getWidth();
	                height = parent.getHeight();
	                double dim = width < height ? (width / 1.2) : (height / 1.2);
	                RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams((int)dim,(int)dim);
	                rlp.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
	                rlp.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
	                rlp.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
	                relativeLayout.setGravity(Gravity.CENTER);
	                relativeLayout.setLayoutParams(rlp);
	                relativeLayout.invalidate();
	                relativeLayout.requestLayout();
	                
	                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(8,((int)dim - 16));
	                lp.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
	                lp.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
	                lp.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
	                line.setGravity(Gravity.CENTER);
	                line.setLayoutParams(lp);
	                line.setBackgroundColor(Color.RED);
	                line.invalidate();
	                line.requestLayout();
	            }
	        });
	        
	        ShapeDrawable rectShapeDrawable = new ShapeDrawable(); // pre defined class
	        // get paint
		    Paint paint = rectShapeDrawable.getPaint();
		
		    // set border color, stroke and stroke width
		    paint.setColor(Color.GREEN);
		    paint.setStyle(Style.STROKE);
		    paint.setStrokeWidth(8); // you can change the value of 5
		    //relativeLayout.setBackgroundDrawable(rectShapeDrawable);
	        
	        
		    relativeLayout.addView(line);
	        ((RelativeLayout) findViewById(getResourceId("id/csZbarScannerViewContainer"))).addView(relativeLayout);
        }
        /* END - ALMAVIVA */
    }

    @Override
    public void onResume ()
    {
        super.onResume();

        try {
            if(whichCamera.equals("front")) {
                int numCams = Camera.getNumberOfCameras();
                CameraInfo cameraInfo = new CameraInfo();
                for(int i=0; i<numCams; i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if(cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                        camera = Camera.open(i);
                    }
                }
            } else {
                camera = Camera.open();
            }

            if(camera == null) throw new Exception ("Error: No suitable camera found.");
        } catch (RuntimeException e) {
            die("Error: Could not open the camera.");
            return;
        } catch (Exception e) {
            die(e.getMessage());
            return;
        }

        Camera.Parameters camParams = camera.getParameters();
        if(flashMode.equals("on")) {
            camParams.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
        } else if(flashMode.equals("off")) {
            camParams.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        } else {
            camParams.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
        }
        try { camera.setParameters(camParams); }
        catch (RuntimeException e) {
            Log.d("csZBar", "Unsupported camera parameter reported for flash mode: "+flashMode);
        }

        tryStartPreview();
    }

    @Override
    public void onPause ()
    {
        releaseCamera();
        super.onPause();
    }

    @Override
    public void onDestroy ()
    {
        scanner.destroy();
        super.onDestroy();
    }

    // Event handlers --------------------------------------------------

    @Override
    public void onBackPressed ()
    {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    // SurfaceHolder.Callback implementation ---------------------------

    @Override
    public void surfaceCreated (SurfaceHolder hld)
    {
        tryStopPreview();
        holder = hld;
        tryStartPreview();
    }

    @Override
    public void surfaceDestroyed (SurfaceHolder holder)
    {
        // No surface == no preview == no point being in this Activity.
        die("The camera surface was destroyed");
    }

    @Override
    public void surfaceChanged (SurfaceHolder hld, int fmt, int w, int h)
    {
        // Sanity check - holder must have a surface...
        if(hld.getSurface() == null) die("There is no camera surface");

        surfW = w;
        surfH = h;
        matchSurfaceToPreviewRatio();

        tryStopPreview();
        holder = hld;
        tryStartPreview();
    }

    // Continuously auto-focus -----------------------------------------

    private AutoFocusCallback autoFocusCb = new AutoFocusCallback()
    {
        public void onAutoFocus(boolean success, Camera camera) {
		   try{
				camera.cancelAutoFocus();
				autoFocusHandler.postDelayed(doAutoFocus, autoFocusInterval);
		   }catch(Exception e){
				
		   }
        }
    };

    private Runnable doAutoFocus = new Runnable()
    {
        public void run() {
            if(camera != null) camera.autoFocus(autoFocusCb);
        }
    };

    // Camera callbacks ------------------------------------------------

    // Receives frames from the camera and checks for barcodes.
    private PreviewCallback previewCb = new PreviewCallback()
    {
        public void onPreviewFrame(byte[] data, Camera camera) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            Image barcode = new Image(size.width, size.height, "Y800");
            barcode.setData(data);

            if (scanner.scanImage(barcode) != 0) {
                String qrValue = "";

                SymbolSet syms = scanner.getResults();
                for (Symbol sym : syms) {
                    qrValue = sym.getData();

                    // Return 1st found QR code value to the calling Activity.
                    Intent result = new Intent ();
                    result.putExtra(EXTRA_QRVALUE, qrValue);
                    setResult(Activity.RESULT_OK, result);
                    finish();
                }

            }
        }
    };

    // Misc ------------------------------------------------------------

    // finish() due to error
    private void die (String msg)
    {
        setResult(RESULT_ERROR);
        finish();
    }

    private int getResourceId (String typeAndName)
    {
        if(package_name == null) package_name = getApplication().getPackageName();
        if(resources == null) resources = getApplication().getResources();
        return resources.getIdentifier(typeAndName, null, package_name);
    }

    // Release the camera resources and state.
    private void releaseCamera ()
    {
        if (camera != null) {
            autoFocusHandler.removeCallbacks(doAutoFocus);
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    // Match the aspect ratio of the preview SurfaceView with the camera's preview aspect ratio,
    // so that the displayed preview is not stretched/squashed.
    private void matchSurfaceToPreviewRatio () {
        if(camera == null) return;
        if(surfW == 0 || surfH == 0) return;

        // Resize SurfaceView to match camera preview ratio (avoid stretching).
        Camera.Parameters params = camera.getParameters();
        Camera.Size size = params.getPreviewSize();
        float previewRatio = (float) size.height / size.width; // swap h and w as the preview is rotated 90 degrees
        float surfaceRatio = (float) surfW / surfH;

        if(previewRatio > surfaceRatio) {
            scannerSurface.setLayoutParams(new FrameLayout.LayoutParams(
                surfW,
                Math.round((float) surfW / previewRatio),
                Gravity.CENTER
            ));
        } else if(previewRatio < surfaceRatio) {
            scannerSurface.setLayoutParams(new FrameLayout.LayoutParams(
                Math.round((float) surfH * previewRatio),
                surfH,
                Gravity.CENTER
            ));
        }
    }

    // Stop the camera preview safely.
    private void tryStopPreview () {
        // Stop camera preview before making changes.
        try {
            camera.stopPreview();
        } catch (Exception e){
          // Preview was not running. Ignore the error.
        }
    }

    // Start the camera preview if possible.
    // If start is attempted but fails, exit with error message.
    private void tryStartPreview () {
        if(holder != null) {
            try {
                // 90 degrees rotation for Portrait orientation Activity.
                camera.setDisplayOrientation(90);

                camera.setPreviewDisplay(holder);
                camera.setPreviewCallback(previewCb);
                camera.startPreview();
                camera.autoFocus(autoFocusCb); // We are not using any of the
                    // continuous autofocus modes as that does not seem to work
                    // well with flash setting of "on"... At least with this
                    // simple and stupid focus method, we get to turn the flash
                    // on during autofocus.
            } catch (IOException e) {
                die("Could not start camera preview: " + e.getMessage());
            }
        }
    }
}
