/*
 * EditServer.java - jEdit server
 * Copyright (C) 1999, 2000, 2001 Slava Pestov
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

package org.gjt.sp.jedit;

import javax.swing.SwingUtilities;
import java.io.*;
import java.net.*;
import java.util.Random;
import org.gjt.sp.util.Log;

/**
 * @author Slava Pestov
 * @version $Id$
 */
public class EditServer extends Thread
{
	EditServer(String portFile)
	{
		super("jEdit server daemon [" + portFile + "]");
		setDaemon(true);
		this.portFile = portFile;

		try
		{
			// Bind to any port on localhost; accept 2 simultaneous
			// connection attempts before rejecting connections
			socket = new ServerSocket(0, 2,
				InetAddress.getByName("127.0.0.1"));
			authKey = Math.abs(new Random().nextInt());
			int port = socket.getLocalPort();

			FileWriter out = new FileWriter(portFile);
			out.write(String.valueOf(port));
			out.write("\n");
			out.write(String.valueOf(authKey));
			out.write("\n");
			out.close();

			Log.log(Log.DEBUG,this,"jEdit server started on port "
				+ socket.getLocalPort());
			Log.log(Log.DEBUG,this,"Authorization key is "
				+ authKey);

			ok = true;
		}
		catch(IOException io)
		{
			/* on some Windows versions, connections to localhost
			 * fail if the network is not running. To avoid
			 * confusing newbies with weird error messages, log
			 * errors that occur while starting the server
			 * as NOTICE, not ERROR */
			Log.log(Log.NOTICE,this,io);
		}
	}

	public boolean isOK()
	{
		return ok;
	}

	public void run()
	{
		try
		{
			for(;;)
			{
				Socket client = socket.accept();
				Log.log(Log.MESSAGE,this,client + ": connected");

				BufferedReader in = new BufferedReader(
					new InputStreamReader(client.getInputStream()));

				try
				{
					int key = Integer.parseInt(in.readLine());
					if(key != authKey)
					{
						Log.log(Log.ERROR,this,
							client + ": wrong"
							+ " authorization key");
						in.close();
						client.close();
						return;
					}
				}
				catch(Exception e)
				{
					Log.log(Log.ERROR,this,
							client + ": invalid"
							+ " authorization key");
					in.close();
					client.close();
					return;
				}

				Log.log(Log.DEBUG,this,client + ": authenticated"
					+ " successfully");
				handleClient(client,in);

				client.close();
			}
		}
		catch(IOException io)
		{
			Log.log(Log.ERROR,this,io);
		}
	}

	// called by client BeanShell scripts
	public static void handleClient(boolean restore, boolean newView, String[] args)
	{
		Buffer buffer = jEdit.openFiles(null,args);
		String splitConfig = null;

		if(restore)
		{
			if(buffer == null)
				splitConfig = jEdit.restoreOpenFiles();
			else if(jEdit.getBooleanProperty("restore.cli"))
				jEdit.restoreOpenFiles();
		}

		if(buffer == null && splitConfig == null)
			buffer = jEdit.newFile(null);

		View view = null;

		if(!newView)
			view = jEdit.getFirstView();

		if(view != null)
		{
			if(buffer != null)
				view.setBuffer(buffer);

			view.requestFocus();
			view.toFront();
		}
		else if(splitConfig != null)
			jEdit.newView(null,splitConfig);
		else
			jEdit.newView(null,buffer);
	}

	// package-private members
	void stopServer()
	{
		stop();
		new File(portFile).delete();
	}

	// private members
	private String portFile;
	private ServerSocket socket;
	private int authKey;
	private boolean ok;

	private void handleClient(Socket client, Reader in)
		throws IOException
	{
		final StringBuffer script = new StringBuffer();
		char[] buf = new char[1024];
		int count;

		while((count = in.read(buf,0,buf.length)) != -1)
		{
			script.append(buf,0,count);
		}

		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				String scriptString = script.toString();
				Log.log(Log.DEBUG,this,scriptString);
				BeanShell.eval(null,scriptString,false);
			}
		});
	}
}
