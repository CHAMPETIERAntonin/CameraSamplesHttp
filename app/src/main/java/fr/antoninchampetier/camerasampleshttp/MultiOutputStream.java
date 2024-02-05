package fr.antoninchampetier.camerasampleshttp;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class MultiOutputStream extends OutputStream
{
	private ArrayList<OutputStream> streams = new ArrayList<>();
	private boolean isStarted = false;

	public boolean addStream(OutputStream stream)
	{
		if(isStarted)
			return false;
		this.streams.add(stream);
		return true;
	}

	public void clear()
	{
		this.streams.clear();
		this.isStarted = false;
	}

	public void write(int var1) throws IOException
	{
		this.isStarted = true;
		for(OutputStream stream : this.streams)
			stream.write(var1);
	}

	public void write(byte[] b) throws IOException
	{
		this.isStarted = true;
		for(OutputStream stream : this.streams)
			stream.write(b);
	}

	public void write(byte[] b, int off, int len) throws IOException
	{
		this.isStarted = true;
		for(OutputStream stream : this.streams)
			stream.write(b, off, len);
	}

	public void flush() throws IOException
	{
		this.isStarted = true;
		for(OutputStream stream : this.streams)
			stream.flush();
	}

	public void close() throws IOException
	{
		this.isStarted = true;
		for(OutputStream stream : this.streams)
		{
			try
			{
				stream.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
}
