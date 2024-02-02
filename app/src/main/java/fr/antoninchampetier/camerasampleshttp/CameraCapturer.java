package fr.antoninchampetier.camerasampleshttp;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.view.View;

import androidx.annotation.NonNull;

import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.size.SizeSelector;
import com.otaliastudios.cameraview.size.SizeSelectors;

import java.io.IOException;

public class CameraCapturer
{
	private static CameraCapturer instance;
	public static CameraCapturer getInstance()
	{
		if(CameraCapturer.instance == null)
			CameraCapturer.instance = new CameraCapturer();

		return CameraCapturer.instance;
	}





	SimpleHTTPServer simpleHTTPServer;
	CameraView cameraView;
	byte[] data;
	boolean isTakingPicture;


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
					CameraCapturer.this.data = result.getData();
					CameraCapturer.this.isTakingPicture = false;
					CameraCapturer.this.notifyAll();
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

				char[] chars = new char[CameraCapturer.this.data.length];

				for(int i=0; i<CameraCapturer.this.data.length; i++)
					chars[i] = (char)CameraCapturer.this.data[i];

				response.setBody(chars);
//				out.write(httpVersion + " 200 OK\n");
//				out.write("\n");
//				out.write("Bien le bjr");
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
	}



	public void takePicture()
	{
		this.isTakingPicture = true;
		this.cameraView.takePictureSnapshot();
	}

	float interval = 5;

	public void setInterval(float interval)
	{
		this.interval = interval;
	}
}
