/*
 * EditServer.java - jEdit server
 * Copyright (C) 1999, 2000 Slava Pestov
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
import java.util.*;
import org.gjt.sp.util.Log;

/**
 * @author Slava Pestov
 * @version $Id$
 */
class EditServer extends Thread
{
	EditServer(String portFile)
	{
		super("jEdit server daemon [" + portFile + "]");
		setDaemon(true);
		this.portFile = portFile;

		try
		{
			socket = new DatagramSocket(); // Bind to any port
			authKey = String.valueOf(Math.abs(new Random().nextInt()));
			int port = socket.getLocalPort();

			FileWriter out = new FileWriter(portFile);
			out.write(String.valueOf(port));
			out.write("\n");
			out.write(authKey);
			out.write("\n");
			out.close();

			Log.log(Log.DEBUG,this,"UDP server started on port "
				+ socket.getLocalPort());
			Log.log(Log.DEBUG,this,"Authorization key is "
				+ authKey);

			// is 1k enough?
			buf = new byte[1024];

			ok = true;
		}
		catch(Exception e)
		{
			/* on some Windows versions, connections to localhost
			 * fail if the network is not running. To avoid
			 * confusing newbies with weird error messages, log
			 * errors that occur while starting the server
			 * as NOTICE, not ERROR */
			Log.log(Log.NOTICE,this,e);
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
				DatagramPacket packet = new DatagramPacket(buf,buf.length);
				socket.receive(packet);

				String host = packet.getAddress().getHostName();
				Log.log(Log.MESSAGE,this,"Got UDP packet from " + host + ":");
				String received = new String(buf,0,packet.getLength(),"UTF8");
				Log.log(Log.DEBUG,this,received);

				StringTokenizer st = new StringTokenizer(received,"\n");

				if(!st.hasMoreTokens())
				{
					Log.log(Log.ERROR,this,"Received empty packet"
						+ " from " + host);
					Log.log(Log.ERROR,this,"Stopping server to"
						+ " prevent further attacks");
					stopServer();
					return;
				}

				String key = st.nextToken();
				if(!key.equals(authKey))
				{
					Log.log(Log.ERROR,this,"Received incorrect"
						+ " authorization key " + key
						+ " from " + host);
					Log.log(Log.ERROR,this,"Stopping server to"
						+ " prevent further attacks");
					stopServer();
					return;
				}

				Log.log(Log.DEBUG,this,host + ": authenticated"
					+ " successfully");

				// send ACK packet to let the client know
				// that we picked it up
				packet = new DatagramPacket(new byte[0],0,
					packet.getAddress(),packet.getPort());
				socket.send(packet);

				handleClient(host,st);
			}
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,this,e);
			stopServer();
		}
	}

	void stopServer()
	{
		stop();
		socket.close();
		new File(portFile).delete();
	}

	// private members
	private String portFile;
	private DatagramSocket socket;
	private String authKey;
	private boolean ok;

	private byte[] buf;

	// Thread-safe wrapper for jEdit.newView()
	private void TSnewView(final Buffer buffer)
	{
		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				View view = jEdit.newView(jEdit.getFirstView(),
					buffer);
				view.requestFocus();
				view.toFront();
			}
		});
	}

	// Thread-safe wrapper for jEdit.newFile()
	private Buffer TSnewFile()
	{
		final Buffer[] retVal = new Buffer[1];
		try
		{
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run()
				{
					retVal[0] = jEdit.newFile(null);
				}
			});
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,this,e);
		}
		return retVal[0];
	}

	// Thread-safe wrapper for jEdit.openFile()
	private Buffer TSopenFiles(final String parent, final String[] args)
	{
		final Buffer[] retVal = new Buffer[1];
		try
		{
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run()
				{
					retVal[0] = jEdit.openFiles(parent,args);
				}
			});
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,this,e);
		}
		return retVal[0];
	}

	// Thread-safe wrapper for Sessions.loadSession()
	private Buffer TSloadSession(final String session)
	{
		final Buffer[] retVal = new Buffer[1];
		try
		{
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run()
				{
					retVal[0] = Sessions.loadSession(session,false);
				}
			});
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,this,e);
		}
		return retVal[0];
	}

	// Thread-safe wrapper for View.setBuffer()
	private void TSsetBuffer(final View view, final Buffer buffer)
	{
		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				view.setBuffer(buffer);
			}
		});
	}

	private void handleClient(String host, StringTokenizer st)
		throws Exception
	{
		boolean readOnly = false;
		boolean newView = false;
		String parent = null;
		String session = (jEdit.getBooleanProperty("saveDesktop")
			? "default" : null);
		boolean endOpts = false;

		View view = null;
		Vector args = new Vector();

		while(st.hasMoreTokens())
		{
			String command = st.nextToken();

			if(endOpts)
				args.addElement(command);
			else
			{
				if(command.equals("--"))
					endOpts = true;
				else if(command.equals("readonly"))
					readOnly = true;
				else if(command.equals("newview"))
					newView = true;
				else if(command.startsWith("parent="))
					parent = command.substring(7);
				else if(command.startsWith("session="))
					session = command.substring(8);
				else if(command.startsWith("nosession"))
					session = null;
				else
				{
					Log.log(Log.ERROR,this,"Got unknown"
						+ " command " + command
						+ " from " + host);
					Log.log(Log.ERROR,this,"Stopping server to"
						+ " prevent further attacks");
					stopServer();
				}
			}
		}

		String[] _args = new String[args.size()];
		args.copyInto(_args);
		Buffer buffer = TSopenFiles(parent,_args);

		// Try loading session, then new file
		if("default".equals(session))
		{
			if(buffer == null)
			{
				// Load default session
				buffer = TSloadSession(session);
			}
		}
		else if(session != null)
		{
			buffer = TSloadSession(session);
		}

		if(buffer == null)
			buffer = TSnewFile();

		// Create new view
		if(!newView)
			view = jEdit.getFirstView();

		if(view != null)
		{
			TSsetBuffer(view,buffer);
			view.requestFocus();
			view.toFront();
		}
		else
			TSnewView(buffer);
	}
}
