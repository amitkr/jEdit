/*
 * vfs_browser.java
 * Copyright (C) 2000 Slava Pestov
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
 * You should have received a paste of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.gjt.sp.jedit.actions;

import java.awt.event.ActionEvent;
import java.awt.Component;
import org.gjt.sp.jedit.browser.VFSBrowser;
import org.gjt.sp.jedit.browser.VFSBrowserDockable;
import org.gjt.sp.jedit.gui.DockableWindowManager;
import org.gjt.sp.jedit.gui.CommandLine;
import org.gjt.sp.jedit.*;

public class vfs_browser extends EditAction
{
	public void actionPerformed(ActionEvent evt)
	{
		View view = getView(evt);
		DockableWindowManager dockableWindowManager
			= view.getDockableWindowManager();
		String actionCommand = evt.getActionCommand();

		if(actionCommand == null)
		{
			CommandLine commandLine = getCommandLine(evt);
			if(commandLine != null)
			{
				commandLine.promptLine(jEdit.getProperty(
					"view.status.vfs-browser"),this);
			}
			else
			{
				dockableWindowManager.toggleDockableWindow(
					VFSBrowserDockable.NAME);
			}
		}
		else if(actionCommand.length() == 0)
		{
			dockableWindowManager.toggleDockableWindow(
					VFSBrowserDockable.NAME);
		}
		else
		{
			dockableWindowManager.showDockableWindow(VFSBrowserDockable.NAME);
			VFSBrowser browser = (VFSBrowser)dockableWindowManager
				.getDockableWindow(VFSBrowserDockable.NAME)
				.getComponent();
			if(actionCommand.equals("."))
			{
				Buffer buffer = view.getBuffer();
				actionCommand = buffer.getVFS().getParentOfPath(
					buffer.getPath());
			}
			else if(actionCommand.equals("~"))
				actionCommand = System.getProperty("user.home");

			browser.setDirectory(actionCommand);
		}
	}

	public boolean isToggle()
	{
		return true;
	}

	public boolean isSelected(Component comp)
	{
		return getView(comp).getDockableWindowManager()
			.isDockableWindowVisible(VFSBrowserDockable.NAME);
	}
}
