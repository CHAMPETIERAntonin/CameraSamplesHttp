package fr.antoninchampetier.camerasampleshttp;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleHTTPServer
{
	private final ExecutorService threadpool = Executors.newCachedThreadPool();
	private ServerSocket socket;

	private HashMap<String, IHTTPHandler> handlers = new HashMap<>();
	Thread s;
	public SimpleHTTPServer(int port)
	{

		try
		{
			this.socket = new ServerSocket(port);
			Log.w("adresss ", this.socket.getInetAddress().getHostAddress());
			this.s = new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					while(true)
					{
						try
						{
							Socket socketClient = SimpleHTTPServer.this.socket.accept();

							SimpleHTTPServer.this.threadpool.submit(new SocketHandler(socketClient));
						}
						catch (IOException e)
						{
							throw new RuntimeException(e);
						}
					}
				}
			});

			this.s.start();
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}



	public void registerHandler(String url, IHTTPHandler handler)
	{
		this.handlers.put(url, handler);
	}



	public class SocketHandler implements Runnable
	{
		private final Socket socketClient;

		public SocketHandler(Socket socketClient)
		{
			this.socketClient = socketClient;
		}

		@Override
		public void run()
		{
			try
			{

				BufferedReader in = new BufferedReader(new InputStreamReader(this.socketClient.getInputStream()));
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(this.socketClient.getOutputStream()));

				HTTPRequest request = new HTTPRequest(in);
				HTTPResponse response = new HTTPResponse();

				response.setHttpVersion(request.getHttpVersion());

				IHTTPHandler handler = SimpleHTTPServer.this.handlers.get(request.url);
				if (handler != null)
				{
					handler.Handle(request, response);
				}
				else
				{
					response.setStatusCode(404);
					response.setReason("Unknown");
				}
				response.generate(out);
				out.flush();
				this.socketClient.close();
			}
			catch(IOException e)
			{
				Log.e("ERROR", "IOException !!");
				e.printStackTrace();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}


	public interface IHTTPHandler
	{
		void Handle(HTTPRequest request, HTTPResponse response) throws Exception;
	}

	public static class HTTPResponse
	{
		private String httpVersion;
		private int statusCode;
		private String reason;

		private HashMap<String, String> headers = new HashMap<>();
		private char[] body;

		public void setStatusCode(int statusCode)
		{
			this.statusCode = statusCode;
		}

		public void setReason(String reason)
		{
			this.reason = reason;
		}

		public void setHttpVersion(String httpVersion)
		{
			this.httpVersion = httpVersion;
		}

		public void setHeader(String name, String value)
		{
			this.headers.put(name, value);
		}

		public void setBody(char[] body)
		{
			this.body = body;
		}


		public void generate(BufferedWriter out) throws IOException
		{
			out.write(this.httpVersion);
			out.write(' ');
			out.write(String.valueOf(this.statusCode));
			out.write(' ');
			out.write(this.reason);
			out.write("\r\n");

			for (Map.Entry<String, String> entry : this.headers.entrySet())
			{
				out.write(entry.getKey());
				out.write(": ");
				out.write(entry.getValue());
				out.write("\r\n");
			}
			out.write("\r\n");

			if(this.body != null)
				out.write(this.body);
		}


	}


	public static class HTTPRequest
	{
		public enum HTTPMethod
		{
			GET,
			POST
		}
		private enum ReadState
		{
			METHOD,
			URL,
			VERSION,
			HEADER_NAME,
			HEADER_VALUE,
			BODY,
		}



		private HTTPMethod method;
		private String url;
		private String httpVersion;

		private HashMap<String, String> headers = new HashMap<>();
		private ArrayList<Character> body = new ArrayList<>();

		public HTTPMethod getMethod()
		{
			return method;
		}

		public String getUrl()
		{
			return url;
		}

		public String getHttpVersion()
		{
			return httpVersion;
		}

		public HashMap<String, String> getHeaders()
		{
			return headers;
		}

		public ArrayList<Character> getBody()
		{
			return body;
		}
		public HTTPRequest(BufferedReader reader) throws IOException
		{
			ReadState state = ReadState.METHOD;
			int character;
			StringBuilder current = new StringBuilder();
			String headerName = null;

			int contentSize = 0;

			while((character = reader.read()) != -1)
			{
				char c = (char) character;

				switch (state)
				{
					case METHOD:
						if(c == ' ')
						{
							state = ReadState.URL;
							String s = current.toString().trim();
							if(s.equals("GET"))
								this.method = HTTPMethod.GET;
							else if (s.equals("POST"))
								this.method = HTTPMethod.POST;
							else
								throw new IOException("Unknown HTTP method : " + s);
							current.setLength(0);
						}
						else
							current.append(c);
						break;
					case URL:
						if(c == ' ')
						{
							state = ReadState.VERSION;
							this.url = current.toString().trim();

							current.setLength(0);
						}
						else
							current.append(c);
						break;
					case VERSION:
						if(c == '\n')
						{
							state = ReadState.HEADER_NAME;
							this.httpVersion = current.toString().trim();

							current.setLength(0);
						}
						else
							current.append(c);
						break;
					case HEADER_NAME:
						if(c == ':')
						{
							state = ReadState.HEADER_VALUE;
							headerName = current.toString().trim();

							current.setLength(0);
						}
						else if (c == '\n')
						{
							headerName = this.headers.get("Content-Length");
							if(headerName == null)
								return;
							contentSize =  Integer.parseInt(headerName);
							if(contentSize == 0)
								return;

							state = ReadState.BODY;
						}
						else
							current.append(c);
						break;
					case HEADER_VALUE:
						if(c == '\n')
						{
							state = ReadState.HEADER_NAME;
							this.headers.put(headerName, current.toString().trim());

							current.setLength(0);
						}
						else
							current.append(c);
						break;
					case BODY:
						this.body.add(c);
						if(this.body.size() == contentSize)
							return;
						break;
				}
			}
		}
	}
}




