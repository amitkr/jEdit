/*
 * Cmd_netscape_open_sel.java - Simple plugin
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

import java.io.IOException;
import java.util.Hashtable;

public class Cmd_netscape_open_sel implements Command
{
	public void exec(Buffer buffer, View view, String arg, Hashtable args)
	{
		String selection = view.getTextArea().getSelectedText();
		if(selection == null)
		{
			view.getToolkit().beep();
			return;
		}
		String[] remoteArgs = { "netscape", "-remote",
			"openURL(" + selection + ")" };
		try
		{
			Process remote = Runtime.getRuntime()
				.exec(remoteArgs);
			if(remote.waitFor() != 0)
			{
				String[] netscapeArgs = { "netscape",
					selection };
				Runtime.getRuntime().exec(netscapeArgs);
			}
		}
		catch(IOException io)
		{
			String[] errorArgs = { io.toString() };
			jEdit.error(view,"ioerror",errorArgs);
		}
		catch(InterruptedException i)
		{
		}
	}
}
