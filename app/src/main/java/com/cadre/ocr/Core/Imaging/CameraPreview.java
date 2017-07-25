/***
  Copyright (c) 2008-2012 CommonsWare, LLC
  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
  by applicable law or agreed to in writing, software distributed under the
  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
  OF ANY KIND, either express or implied. See the License for the specific
  language governing permissions and limitations under the License.
  
  From _The Busy Coder's Guide to Advanced Android Development_
    http://commonsware.com/AdvAndroid
    
  Modified by Juan Carlos Sedano Salas
  I took the code of the camera preview out of the activity
  and put it on a costume SurfaceView.
  Added a way to capture a portion of the bitmap, 
  original code can be found here: 
  https://github.com/commonsguy/cw-advandroid/blob/master/Camera/Preview/src/com/commonsware/android/camera/PreviewDemo.java
 */
package com.cadre.ocr.Core.Imaging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.Toast;

public class CameraPreview extends SurfaceView implements
		SurfaceHolder.Callback {
	private SurfaceHolder cameraPreviewHolder;
	private Camera camera;
	private Context context;
	private boolean cameraConfigured = false;
	private boolean inPreview = false;
	private Size previewSize;
	private byte yuv[];
	private ImageView imageView;

	public CameraPreview(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
		cameraPreviewHolder = getHolder();
		cameraPreviewHolder.addCallback(this);
		// we need this for compatibility
		cameraPreviewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	public void releaseCamera() {
		if (inPreview) {
			camera.stopPreview();
			camera.release();
			camera = null;
			inPreview = false;
		}
	}
	
	public void resumePreview(){
		if (!inPreview) {
			camera = Camera.open();
		}
	}

	private void initPreview(int width, int height) {
		if (camera != null && cameraPreviewHolder.getSurface() != null) {
			try {
				camera.setPreviewDisplay(cameraPreviewHolder);
			} catch (Throwable t) {
				Log.e("CameraPreview", "Exception in setPreviewDisplay()", t);
				Toast.makeText(context, t.getMessage(), Toast.LENGTH_LONG)
						.show();
			}
			if (!cameraConfigured) {
				Parameters parameters = camera.getParameters();
				previewSize = getBestPreviewSize(width, height, parameters);
				if (previewSize != null) {
					parameters.setPreviewSize(previewSize.width,
							previewSize.height);
					camera.setParameters(parameters);
					cameraConfigured = true;
				}
			}
		}
	}

	private Size getBestPreviewSize(int width, int height, Parameters parameters) {
		// Initialize new size 'result' to null
		Size result = null;
		// Cycle through all the sizes in the parameter's supported preview sizes
		for (Size size : parameters.getSupportedPreviewSizes()) {
			// if current size's width and height are less than or equal to width && height passed in...
			if (size.width <= width && size.height <= height) {
				// And if we haven't already set the result
				if (result == null) {
					// Set the result to the current size
					result = size;
				} else {
					int resultArea = result.width * result.height;
					int newArea = size.width * size.height;
					if (newArea > resultArea) {
						result = size;
					}
				}

			}
		}
		return result;
	}

	private void startPreview() {
		if (cameraConfigured && camera != null) {
			camera.startPreview();
			yuv = new byte[getBufferSize()];
			camera.addCallbackBuffer(yuv);
			camera.setPreviewCallbackWithBuffer(new PreviewCallback() {
				public synchronized void onPreviewFrame(byte[] data, Camera c) {
					if (camera != null) { 
						camera.addCallbackBuffer(yuv);
					}
				}
			});
			inPreview = true;
		}
	}

	// Initialize the preview and start it from here
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		initPreview(width, height);
		startPreview();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {

	}

	private int getBufferSize() {
		int pixelformat = ImageFormat.getBitsPerPixel(camera.getParameters()
				.getPreviewFormat());
		// Multiply each pixel by the number bits per pixel, then divide by 8 to return bytes
		int bufSize = previewSize.width * previewSize.height * pixelformat / 8;
		return bufSize;
	}

	public Bitmap getBitmap(int left, int top, int right, int bottom)
			throws IOException {
		Bitmap bitmap = null;
		//create new YuvImage using the entire screen size. pass in the 'yuv' byte array to store it
		YuvImage yuvimage = new YuvImage(yuv, ImageFormat.NV21, previewSize.width,
				previewSize.height, null);

		// Create an output stream
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();

		// Compress the image to JPG using the rectangle dimensions we passed in
		yuvimage.compressToJpeg(new Rect(left, top, right, bottom), 100, outStream);

		//Create a bitmap out of the output stream
		bitmap = BitmapFactory.decodeByteArray(outStream.toByteArray(), 0,
				outStream.size());
		yuvimage = null;
		outStream = null;
		return bitmap;
	}
}
