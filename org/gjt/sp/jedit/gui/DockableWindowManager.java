/*
 * DockableWindowManager.java - manages dockable windows
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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.gjt.sp.jedit.gui;

import org.gjt.sp.jedit.browser.VFSBrowserDockable;
import org.gjt.sp.jedit.msg.CreateDockableWindow;
import org.gjt.sp.jedit.*;
import javax.swing.JPanel;
import java.awt.event.*;
import java.awt.*;
import java.util.*;

/**
 * Manages dockable windows.
 * @author Slava Pestov
 * @version $Id$
 * @since jEdit 2.6pre3
 */
public class DockableWindowManager extends JPanel
{
	/**
	 * Floating position.
	 * @since jEdit 2.6pre3
	 */
	public static final String FLOATING = "floating";

	/**
	 * Top position.
	 * @since jEdit 2.6pre3
	 */
	public static final String TOP = "top";

	/**
	 * Left position.
	 * @since jEdit 2.6pre3
	 */
	public static final String LEFT = "left";

	/**
	 * Bottom position.
	 * @since jEdit 2.6pre3
	 */
	public static final String BOTTOM = "bottom";

	/**
	 * Right position.
	 * @since jEdit 2.6pre3
	 */
	public static final String RIGHT = "right";

	/**
	 * Creates a new dockable window manager.
	 * @param view The view
	 * @since jEdit 2.6pre3
	 */
	public DockableWindowManager(View view)
	{
		super(new BorderLayout());

		this.view = view;
		windows = new Hashtable();
	}

	/**
	 * Adds any dockables set to auto-open.
	 * @since jEdit 2.6pre3
	 */
	public void init()
	{
		Object[] dockables = EditBus.getNamedList(DockableWindow
			.DOCKABLE_WINDOW_LIST);
		if(dockables != null)
		{
			for(int i = 0; i < dockables.length; i++)
			{
				String name = (String)dockables[i];
				if(jEdit.getBooleanProperty(name + ".auto-open"))
					addDockableWindow(name);
			}
		}
	}

	/**
	 * Adds the dockable window with the specified name to this dockable
	 * window manager.
	 * @param name The dockable window name
	 * @since jEdit 2.6pre3
	 */
	public void addDockableWindow(String name)
	{
		Entry entry = (Entry)windows.get(name);
		if(entry != null)
		{
			entry.container.showDockableWindow(entry.win);
			return;
		}

		String position = jEdit.getProperty(name + ".dock-position",
			FLOATING);

		CreateDockableWindow msg = new CreateDockableWindow(view,name,
			position);
		EditBus.send(msg);

		DockableWindow win = msg.getDockableWindow();
		if(win == null)
			throw new IllegalArgumentException("Unknown dockable window: "
				+ name);

		addDockableWindow(win,position);
	}

	/**
	 * Adds the specified dockable window to this dockable window manager.
	 * The position will be loaded from the properties.
	 * @param win The dockable window
	 * @since jEdit 2.6pre3
	 */
	public void addDockableWindow(DockableWindow win)
	{
		String name = win.getName();
		String position = jEdit.getProperty(name + ".dock-position",
			FLOATING);

		addDockableWindow(win,position);
	}

	/**
	 * Adds the specified dockable window to this dockable window manager.
	 * @param win The dockable window
	 * @param pos The window position
	 * @since jEdit 2.6pre3
	 */
	public void addDockableWindow(DockableWindow win, String position)
	{
		String name = win.getName();
		if(windows.get(name) != null)
		{
			throw new IllegalArgumentException("This DockableWindowManager"
				+ " already has a window named " + name);
		}

		DockableWindowContainer container;
		if(position.equals(FLOATING))
			container = new DockableWindowContainer.Floating(this);
		else
		{
			if(position.equals(TOP))
			{
				if(top == null)
				{
					top = new DockableWindowContainer.TabbedPane(TOP);
					add(BorderLayout.NORTH,top);
				}
				container = top;
			}
			else if(position.equals(LEFT))
			{
				if(left == null)
				{
					left = new DockableWindowContainer.TabbedPane(LEFT);
					add(BorderLayout.WEST,left);
				}
				container = left;
			}
			else if(position.equals(BOTTOM))
			{
				if(bottom == null)
				{
					bottom = new DockableWindowContainer.TabbedPane(BOTTOM);
					add(BorderLayout.SOUTH,bottom);
				}
				container = bottom;
			}
			else if(position.equals(RIGHT))
			{
				if(right == null)
				{
					right = new DockableWindowContainer.TabbedPane(RIGHT);
					add(BorderLayout.EAST,right);
				}
				container = right;
			}
			else
			{
				throw new InternalError("Unknown position: " + position);
			}
		}

		container.addDockableWindow(win);
		Entry entry = new Entry(win,position,container);
		windows.put(name,entry);
	}

	/**
	 * Removes the specified dockable window from this dockable window manager.
	 * @param name The dockable window name
	 * @since jEdit 2.6pre3
	 */
	public void removeDockableWindow(String name)
	{
		Entry entry = (Entry)windows.get(name);
		if(entry == null)
			throw new IllegalArgumentException("This DockableWindowManager"
				+ " does not have a window named " + name);

		saveEntry(entry);

		entry.container.removeDockableWindow(entry.win);
		windows.remove(name);
	}

	/**
	 * Toggles the visibility of the specified dockable window.
	 * @param name The dockable window name
	 */
	public void toggleDockableWindow(String name)
	{
		Entry entry = (Entry)windows.get(name);
		if(entry != null)
			removeDockableWindow(name);
		else
			addDockableWindow(name);
	}

	/**
	 * Returns if the specified dockable window is visible.
	 * @param name The dockable window name
	 */
	public boolean isDockableWindowVisible(String name)
	{
		return windows.get(name) != null;
	}

	/**
	 * Called when the view is being closed.
	 * @since jEdit 2.6pre3
	 */
	public void close()
	{
		Enumeration enum = windows.elements();
		while(enum.hasMoreElements())
		{
			Entry entry = (Entry)enum.nextElement();
			saveEntry(entry);
			entry.container.removeDockableWindow(entry.win);
		}

		if(top != null)
			top.saveDimension();
		if(left != null)
			left.saveDimension();
		if(bottom != null)
			bottom.saveDimension();
		if(right != null)
			right.saveDimension();
	}

	// private members
	private View view;
	private Hashtable windows;
	private DockableWindowContainer.TabbedPane left;
	private DockableWindowContainer.TabbedPane right;
	private DockableWindowContainer.TabbedPane top;
	private DockableWindowContainer.TabbedPane bottom;

	static
	{
		EditBus.addToBus(new DefaultFactory());
		EditBus.addToNamedList(DockableWindow.DOCKABLE_WINDOW_LIST,"vfs.browser");
	}

	private void saveEntry(Entry entry)
	{
		jEdit.setProperty(entry.win.getName() + ".dock-position",
			entry.position);
		entry.container.saveDockableWindow(entry.win);
	}

	static class Entry
	{
		DockableWindow win;
		String position;
		DockableWindowContainer container;

		Entry(DockableWindow win, String position,
			DockableWindowContainer container)
		{
			this.win = win;
			this.position = position;
			this.container = container;
		}
	}

	// factory for creating the dockables built into the jEdit core
	// (VFS browser, etc)
	static class DefaultFactory implements EBComponent
	{
		public void handleMessage(EBMessage msg)
		{
			if(msg instanceof CreateDockableWindow)
			{
				CreateDockableWindow cmsg = (CreateDockableWindow)msg;
				String name = cmsg.getDockableWindowName();
				if(name.equals("vfs.browser"))
				{
					cmsg.setDockableWindow(new VFSBrowserDockable(
						cmsg.getView(),null));
				}
			}
		}
	}
}

/*
 * Change Log:
 * $Log$
 * Revision 1.1  2000/08/13 07:35:24  sp
 * Dockable window API
 *
 */
