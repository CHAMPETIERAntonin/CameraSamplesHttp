package fr.antoninchampetier.camerasampleshttp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.otaliastudios.cameraview.BitmapCallback;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraUtils;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.internal.ExifHelper;
import com.otaliastudios.cameraview.internal.WorkerHandler;
import com.otaliastudios.cameraview.size.SizeSelector;
import com.otaliastudios.cameraview.size.SizeSelectors;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class CameraCapturer
{
	private static CameraCapturer instance;
	public static CameraCapturer getInstance()
	{
		if(CameraCapturer.instance == null)
			CameraCapturer.instance = new CameraCapturer();

		return CameraCapturer.instance;
	}


	MultiOutputStream multiOutputStream = new MultiOutputStream();



	SimpleHTTPServer simpleHTTPServer;
	CameraView cameraView;
	byte[] data;
	boolean isTakingPicture;

	Quality quality = Quality.Low;


	public void setCameraView(CameraView cameraView)
	{
		this.cameraView = cameraView;
		this.cameraView.setFrameProcessingFormat(ImageFormat.JPEG);
		this.cameraView.setPreviewFrameRate(2f);

		Activity activity = ((Activity)this.cameraView.getContext());
		View dcv = activity.getWindow().getDecorView();

		SizeSelector s = SizeSelectors.and(SizeSelectors.minWidth(dcv.getWidth() + 10), SizeSelectors.minHeight(dcv.getHeight() + 10));
		this.cameraView.setPreviewStreamSize(s);
		this.cameraView.setPictureSize(s);



		this.cameraView.addCameraListener(new CameraListener()
		{
			@Override
			public void onPictureTaken(@NonNull PictureResult result)
			{
				super.onPictureTaken(result);
				synchronized (CameraCapturer.this)
				{
					WorkerHandler.execute(
							new Runnable()
							{
								@Override
								public void run()
								{

									Bitmap bitmap = CameraCapturer.decodeBitmap(result.getData(), Integer.MAX_VALUE, Integer.MAX_VALUE, new BitmapFactory.Options(), result.getRotation());
									if (bitmap != null)
										bitmap.compress(Bitmap.CompressFormat.JPEG, 90, CameraCapturer.this.multiOutputStream);

									CameraCapturer.this.multiOutputStream.clear();
									CameraCapturer.this.isTakingPicture = false;
									synchronized (CameraCapturer.this)
									{
										CameraCapturer.this.notifyAll();
									}

								}
							}
					);
				}
			}
		});
	}

	public CameraCapturer()
	{
		this.simpleHTTPServer = new SimpleHTTPServer(8080);

		this.simpleHTTPServer.registerHandler("/shot.jpg", new SimpleHTTPServer.IHTTPHandler()
		{
			@Override
			public void Handle(SimpleHTTPServer.HTTPRequest req, SimpleHTTPServer.HTTPResponse response) throws Exception
			{

				response.setStatusCode(200);
				response.setReason("OK");
				response.setHeader("Hostname", "phone");
				response.setHeader("Content-Type", "image/jpeg");

				response.generate();
				CameraCapturer.this.multiOutputStream.addStream(response.getStream());

				synchronized (CameraCapturer.this)
				{

					if (CameraCapturer.this.isTakingPicture)
						CameraCapturer.this.wait();
					else
					{
						CameraCapturer.this.takePicture();
						CameraCapturer.this.wait();
					}
				}

				//response.setBody(CameraCapturer.this.data);
			}
		});



		this.simpleHTTPServer.registerHandler("/interval", new SimpleHTTPServer.IHTTPHandler()
		{
			@Override
			public void Handle(SimpleHTTPServer.HTTPRequest req, SimpleHTTPServer.HTTPResponse response) throws IOException
			{
				if(req.getMethod() == SimpleHTTPServer.HTTPRequest.HTTPMethod.POST)
				{
					response.setStatusCode(200);
					response.setReason("OK");
					response.setHeader("Hostname", "phone");

					if(req.getBody().size() >= 4)
					{
						StringBuilder b = new StringBuilder(4);

						for(int i=0; i<4; i++)
							b.append(req.getBody().get(i));

						try
						{
							float interval = Float.parseFloat(b.toString());

							CameraCapturer.getInstance().setInterval(interval);
						}
						catch (NumberFormatException e)
						{
							e.printStackTrace();
						}

					}

				}
				else
				{
					response.setStatusCode(400);
					response.setReason("Unknown");
					response.setHeader("Hostname", "phone");
				}
			}
		});


		this.simpleHTTPServer.registerHandler("/quality", new SimpleHTTPServer.IHTTPHandler()
		{
			@Override
			public void Handle(SimpleHTTPServer.HTTPRequest request, SimpleHTTPServer.HTTPResponse response) throws Exception
			{
				switch (request.getMethod())
				{
					case GET:
						response.setStatusCode(200);
						response.setReason("Ok");
						response.setHeader("Hostname", "phone");
						response.setHeader("Content-Type", "text/plain");

						switch (CameraCapturer.this.quality)
						{
							case Low:
								response.setBody("Low".getBytes(StandardCharsets.US_ASCII));
								break;
							case High:
								response.setBody("High".getBytes(StandardCharsets.US_ASCII));
								break;
						}
						break;

					case POST:
						String value = request.getParameters().get("value");
						boolean success = false;
						if(value != null)
						{
							if(value.equals("Low"))
							{
								CameraCapturer.this.quality = Quality.Low;
								success = true;
							}
							else if(value.equals("High"))
							{
								CameraCapturer.this.quality = Quality.High;
								success = true;

							}
						}

						if(success)
						{
							response.setStatusCode(200);
							response.setReason("Ok");
							response.setHeader("Hostname", "phone");
						}
						else
						{
							response.setStatusCode(400);
							response.setReason("Bad Request, Unknown Parameter");
							response.setHeader("Hostname", "phone");
						}
						break;
				}
			}
		});
	}



	public void takePicture()
	{
		this.isTakingPicture = true;
		switch (this.quality)
		{
			case Low:
				this.cameraView.takePictureSnapshot();
				break;
			case High:
				this.cameraView.takePicture();
				break;
		}
	}

	float interval = 5;

	public void setInterval(float interval)
	{
		this.interval = interval;
	}




	// Ignores flipping, but it should be super rare.
	@SuppressWarnings("TryFinallyCanBeTryWithResources")
	@Nullable
	private static Bitmap decodeBitmap(@NonNull byte[] source,
									   int maxWidth,
									   int maxHeight,
									   @NonNull BitmapFactory.Options options,
									   int rotation)
	{
		if (maxWidth <= 0) maxWidth = Integer.MAX_VALUE;
		if (maxHeight <= 0) maxHeight = Integer.MAX_VALUE;
		int orientation;
		boolean flip;
		if (rotation == -1)
		{
			InputStream stream = null;
			try
			{
				// http://sylvana.net/jpegcrop/exif_orientation.html
				stream = new ByteArrayInputStream(source);
				ExifInterface exif = new ExifInterface(stream);
				int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
						ExifInterface.ORIENTATION_NORMAL);
				orientation = ExifHelper.getOrientation(exifOrientation);
				flip = exifOrientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
						exifOrientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
						exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE ||
						exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE;
				Log.i("decodeBitmap:", "got orientation from EXIF. " + orientation);
			}
			catch (IOException e)
			{
				Log.e("decodeBitmap:", "could not get orientation from EXIF.", e);
				orientation = 0;
				flip = false;
			}
			finally
			{
				if (stream != null)
				{
					try
					{
						stream.close();
					} catch (Exception ignored) { }
				}
			}
		}
		else
		{
			orientation = rotation;
			flip = false;
			Log.i("decodeBitmap:", "got orientation from constructor. " +  orientation);
		}

		Bitmap bitmap;
		try
		{
			if (maxWidth < Integer.MAX_VALUE || maxHeight < Integer.MAX_VALUE) {
				options.inJustDecodeBounds = true;
				BitmapFactory.decodeByteArray(source, 0, source.length, options);

				int outHeight = options.outHeight;
				int outWidth = options.outWidth;
				if (orientation % 180 != 0) {
					//noinspection SuspiciousNameCombination
					outHeight = options.outWidth;
					//noinspection SuspiciousNameCombination
					outWidth = options.outHeight;
				}

				options.inSampleSize = computeSampleSize(outWidth, outHeight, maxWidth, maxHeight);
				options.inJustDecodeBounds = false;
				bitmap = BitmapFactory.decodeByteArray(source, 0, source.length, options);
			}
			else
			{
				bitmap = BitmapFactory.decodeByteArray(source, 0, source.length);
			}

			if (orientation != 0 || flip)
			{
				Matrix matrix = new Matrix();
				matrix.setRotate(orientation);
				Bitmap temp = bitmap;
				bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
						bitmap.getHeight(), matrix, true);
				temp.recycle();
			}
		}
		catch (OutOfMemoryError e)
		{
			bitmap = null;
		}
		return bitmap;
	}

	private static int computeSampleSize(int width, int height, int maxWidth, int maxHeight) {
		// https://developer.android.com/topic/performance/graphics/load-bitmap.html
		int inSampleSize = 1;
		if (height > maxHeight || width > maxWidth)
		{
			while ((height / inSampleSize) >= maxHeight
					|| (width / inSampleSize) >= maxWidth)
			{
				inSampleSize *= 2;
			}
		}
		return inSampleSize;
	}




	private enum Quality
	{
		High,
		Low
	}
}
