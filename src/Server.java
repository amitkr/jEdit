/*
 * Server.java - jEdit server
 * Copyright (C) 1998 Slava Pestov
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import java.io.*;
import java.net.*;
import java.util.Random;

public class Server extends Thread
{
	private File portFile;
	private ServerSocket server;
	private long authInfo;

	public Server(File portFile)
	{
		super("***jEdit server thread***");
		this.portFile = portFile;
		start();
	}

	public void run()
	{
		if(portFile.exists())
		{
			// Maybe the server crashed?
			try
			{
				BufferedReader in = new BufferedReader(
					new FileReader(portFile));
				Socket socket = new Socket("localhost",
					Integer.parseInt(in.readLine()));
				DataOutputStream out = new DataOutputStream(
					socket.getOutputStream());
				out.writeBytes(in.readLine());
				socket.close();
				in.close();
				System.out.println("jEdit server already"
					+ " running");
				return;
			}
			catch(Exception e)
			{
				System.out.println("Stale port file deleted");
			}
		}
		try
		{
			server = new ServerSocket(0);
			int port = server.getLocalPort();
			
			Random random = new Random();
			authInfo = Math.abs(random.nextLong());
			
			BufferedWriter out = new BufferedWriter(
				new FileWriter(portFile));
			out.write(String.valueOf(port));
			out.write('\n');
			out.write(String.valueOf(authInfo));
			out.write('\n');
			out.close();
			for(;;)
			{
				Socket client = server.accept();
				System.out.println("Connection from "
					+ client.getInetAddress());
				doClient(client);
			}
		}
		catch(IOException io)
		{
			io.printStackTrace();
		}
	}

	private void doClient(Socket client)
	{
		View view = null;
		String authString = null;
		try
		{
			BufferedReader in =
				new BufferedReader(new InputStreamReader(
					client.getInputStream()));
			authString = in.readLine();
			long auth = Long.parseLong(authString);
			if(auth != authInfo)
			{
				System.err.println("Invalid authInfo: "
					+ auth);				
				client.close();
				return;
			}
			String filename;
			String cwd = "";
			boolean endOpts = false;
			boolean readOnly = false;
			while((filename = in.readLine()) != null)
			{
				if(filename.startsWith("-") && !endOpts)
				{
					if(filename.equals("--"))
						endOpts = true;
					else if(filename.equals("-readonly"))
						readOnly = true;
					else if(filename.startsWith("-cwd="))
						cwd = filename.substring(5);
				}
				else
				{
					if(view == null)
						view = jEdit.buffers
							.newView(null);
					if(filename.length() != 0)
						jEdit.buffers.openFile(view,
							cwd,filename,readOnly,
							false);
				}
			}
		}
		catch(NumberFormatException nf)
		{
			System.err.println("Invalid auth info: " + authString);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		try
		{
			client.close();
		}
		catch(IOException io)
		{
			io.printStackTrace();
		}
	}

	public void stopServer()
	{
		if(server == null)
			return;
		stop();
		try
		{
			server.close();
			server = null;
		}
		catch(IOException io)
		{
			io.printStackTrace();
		}
		portFile.delete();
	}
}
