/*
 * jEdit.java - Main class of the jEdit editor
 * Copyright (C) 1998, 1999, 2000 Slava Pestov
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

import javax.swing.text.Element;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.MessageFormat;
import java.util.*;
import org.gjt.sp.jedit.msg.*;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.search.SearchAndReplace;
import org.gjt.sp.jedit.textarea.DefaultInputHandler;
import org.gjt.sp.jedit.textarea.InputHandler;
import org.gjt.sp.util.Log;

/**
 * The main class of the jEdit text editor.
 * @author Slava Pestov
 * @version $Id$
 */
public class jEdit
{
	/**
	 * Returns the jEdit version as a human-readable string.
	 */
	public static String getVersion()
	{
		return MiscUtilities.buildToVersion(getBuild());
	}

	/**
	 * Returns the internal version. String.compareTo() can be used
	 * to compare different internal versions.
	 */
	public static String getBuild()
	{
		// (major) (minor) (<99 = preX, 99 = final) (bug fix)
		return "02.03.07.00";
	}

	/**
	 * The main method of the jEdit application.
	 * <p>
	 * This should never be invoked directly.
	 * @param args The command line arguments
	 */
	public static void main(String[] args)
	{
		// for developers: run 'jedit 0' to get extensive logging
		int level = Log.WARNING;
		if(args.length >= 1)
		{
			String levelStr = args[0];
			if(levelStr.length() == 1 && Character.isDigit(
				levelStr.charAt(0)))
			{
				level = Integer.parseInt(levelStr);
				args[0] = null;
			}
		}

		// Parse command line
		boolean endOpts = false;
		boolean readOnly = false;
		boolean reuseView = false;
		settingsDirectory = MiscUtilities.constructPath(
			System.getProperty("user.home"),".jedit");
		String portFile = "server";
		boolean defaultSession = true;
		session = "default";
		boolean showSplash = true;

		for(int i = 0; i < args.length; i++)
		{
			String arg = args[i];
			if(arg == null)
				continue;
			else if(arg.length() == 0)
				args[i] = null;
			else if(arg.startsWith("-") && !endOpts)
			{
				if(arg.equals("--"))
					endOpts = true;
				else if(arg.equals("-usage"))
				{
					version();
					System.err.println();
					usage();
					System.exit(1);
				}
				else if(arg.equals("-version"))
				{
					version();
					System.exit(1);
				}
				else if(arg.equals("-nosettings"))
					settingsDirectory = null;
				else if(arg.startsWith("-settings="))
					settingsDirectory = arg.substring(10);
				else if(arg.startsWith("-noserver"))
					portFile = null;
				else if(arg.startsWith("-server="))
					portFile = arg.substring(8);
				else if(arg.equals("-nosession"))
					session = null;
				else if(arg.startsWith("-session="))
				{
					session = arg.substring(9);
					defaultSession = false;
				}
				else if(arg.equals("-nosplash"))
					showSplash = false;
				else if(arg.equals("-readonly"))
					readOnly = true;
				else if(arg.equals("-reuseview"))
					reuseView = true;
				else
				{
					System.err.println("Unknown option: "
						+ arg);
					usage();
					System.exit(1);
				}
				args[i] = null;
			}
		}

		if(settingsDirectory != null && portFile != null)
			portFile = MiscUtilities.constructPath(settingsDirectory,portFile);
		else
			portFile = null;

		if(session != null)
			session = Sessions.createSessionFileName(session);

		// Connect to server
		String userDir = System.getProperty("user.dir");

		if(portFile != null && new File(portFile).exists())
		{
			int port, key;
			try
			{
				BufferedReader in = new BufferedReader(new FileReader(portFile));
				port = Integer.parseInt(in.readLine());
				key = Integer.parseInt(in.readLine());
				in.close();

				Socket socket = new Socket(InetAddress.getLocalHost(),port);
				Writer out = new OutputStreamWriter(socket.getOutputStream());
				out.write(String.valueOf(key));
				out.write('\n');

				if(!defaultSession && session != null)
					out.write("session=" + session + "\n");
				if(readOnly)
					out.write("readonly\n");
				if(reuseView)
					out.write("reuseview\n");
				out.write("parent=" + userDir + "\n");
				out.write("--\n");

				for(int i = 0; i < args.length; i++)
				{
					if(args[i] != null)
					{
						out.write(args[i]);
						out.write('\n');
					}
				}

				out.close();

				System.exit(0);
			}
			catch(Exception e)
			{
				Log.log(Log.ERROR,jEdit.class,e);
			}
		}

		// Show the kool splash screen
		if(showSplash)
			GUIUtilities.showSplashScreen();

		// Get things rolling
		Log.init(true,level);
		initMisc();
		initSystemProperties();
		GUIUtilities.advanceProgress("Loading plugins...");
		initPlugins();
		GUIUtilities.advanceProgress("Loading user properties...");
		initUserProperties();
		if(settingsDirectory != null)
		{
			String history = MiscUtilities.constructPath(
				settingsDirectory,"history");
			HistoryModel.loadHistory(history);
		}

		// Buffer sort
		sortBuffers = "on".equals(getProperty("sortBuffers"));
		sortByName = "on".equals(getProperty("sortByName"));

		propertiesChanged();
		initRecent();
		initPLAF();
		SearchAndReplace.load();
		Abbrevs.load();
		GUIUtilities.advanceProgress("Loading actions and modes...");
		initActions();
		initModes();
		initKeyBindings();
		Macros.loadMacros();

		// Start plugins
		GUIUtilities.advanceProgress("Starting plugins...");
		JARClassLoader.initPlugins();

		// Preload menu and tool bar models
		GUIUtilities.advanceProgress("Loading GUI...");

		GUIUtilities.loadMenuBarModel("view.mbar");
		GUIUtilities.loadMenuModel("view.context");
		GUIUtilities.loadMenuModel("gutter.context");
		if("on".equals(getProperty("view.showToolbar")))
			GUIUtilities.loadToolBarModel("view.toolbar");

		// Start server
		if(portFile != null)
			server = new EditServer(portFile);

		// Load files specified on the command line
		GUIUtilities.advanceProgress("Opening files...");

		for(int i = 0; i < args.length; i++)
		{
			if(args[i] == null)
				continue;
			openFile(null,userDir,args[i],readOnly,false);
		}

		Buffer buffer = null;
		if(bufferCount == 0)
		{
			if(defaultSession)
			{
				if(session != null && "on".equals(getProperty("saveDesktop")))
					buffer = Sessions.loadSession(session,true);
			}
			else
				buffer = Sessions.loadSession(session,true);
		}

		if(bufferCount == 0)
			newFile(null);

		GUIUtilities.advanceProgress("Creating initial view...");

		// Create the view and hide the splash screen.
		// Paranoid thread safety courtesy of Sun.
		final Buffer _buffer = buffer;

		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				EditBus.send(new EditorStarted(null));

				newView(null,_buffer);
				GUIUtilities.hideSplashScreen();
				Log.log(Log.MESSAGE,jEdit.class,"Startup "
					+ "complete");
			}
		});
	}

	/**
	 * Loads the properties from the specified input stream. This
	 * calls the <code>load()</code> method of the properties object
	 * and closes the stream.
	 * @param in The input stream
	 * @exception IOException if an I/O error occured
	 */
	public static void loadProps(InputStream in)
		throws IOException
	{
		props.load(new BufferedInputStream(in));
		in.close();
	}

	/**
	 * Fetches a property, returning null if it's not defined.
	 * @param name The property
	 */
	public static final String getProperty(String name)
	{
		return props.getProperty(name);
	}

	/**
	 * Fetches a property, returning the default value if it's not
	 * defined.
	 * @param name The property
	 * @param def The default value
	 */
	public static final String getProperty(String name, String def)
	{
		return props.getProperty(name,def);
	}

	/**
	 * Returns the property with the specified name, formatting it with
	 * the <code>java.text.MessageFormat.format()</code> method.
	 * @param name The property
	 * @param args The positional parameters
	 */
	public static final String getProperty(String name, Object[] args)
	{
		if(name == null)
			return null;
		if(args == null)
			return props.getProperty(name,name);
		else
			return MessageFormat.format(props.getProperty(name,
				name),args);
	}

	/**
	 * Sets a property to a new value.
	 * @param name The property
	 * @param value The new value
	 */
	public static final void setProperty(String name, String value)
	{
		/* if value is null:
		 * - if default is null, unset user prop
		 * - else set user prop to ""
		 * else
		 * - if default equals value, ignore
		 * - if default doesn't equal value, set user
		 */
		if(value == null || value.length() == 0)
		{
			String prop = (String)defaultProps.get(name);
			if(prop == null || prop.length() == 0)
				props.remove(name);
			else
				props.put(name,"");
		}
		else
		{
			if(!value.equals(getProperty(name)))
				props.put(name,value);
		}
	}

	/**
	 * Sets a property to a new value. This method does not override
	 * properties set in the user property list, nor are the
	 * properties set with this method saved in the user properties.
	 * @param name The property
	 * @param value The new value
	 */
	public static final void setDefaultProperty(String name, String value)
	{
		defaultProps.put(name,value);
	}

	/**
	 * Unsets (clears) a property.
	 * @param name The property
	 */
	public static final void unsetProperty(String name)
	{
		if(defaultProps.get(name) != null)
			props.put(name,"");
		else
			props.remove(name);
	}

	/**
	 * Reloads various settings from the properties.
	 */
	public static void propertiesChanged()
	{
		// Auto save
		if(autosave != null)
			autosave.interrupt();

		autosave = new Autosave();

		// Recent files
		try
		{
			maxRecent = Integer.parseInt(getProperty("recent"));
		}
		catch(NumberFormatException nf)
		{
			Log.log(Log.ERROR,jEdit.class,nf);
			maxRecent = 8;
		}

		// File filters might have changed
		GUIUtilities.chooser = null;

		EditBus.send(new PropertiesChanged(null));
	}

	/**
	 * Loads all plugins in a directory.
	 * @param directory The directory
	 */
	public static void loadPlugins(String directory)
	{
		Log.log(Log.MESSAGE,jEdit.class,"Scanning directory: "
			+ directory);

		File file = new File(directory);
		if(!(file.exists() || file.isDirectory()))
			return;
		String[] plugins = file.list();
		if(plugins == null)
			return;

		MiscUtilities.quicksort(plugins,new MiscUtilities.StringICaseCompare());
		for(int i = 0; i < plugins.length; i++)
		{
			String plugin = plugins[i];
			if(!plugin.toLowerCase().endsWith(".jar"))
				continue;
			try
			{
				String path = MiscUtilities.constructPath(
					directory,plugin);
				Log.log(Log.DEBUG,jEdit.class,
					"Scanning JAR file: " + path);
				new JARClassLoader(path);
			}
			catch(IOException io)
			{
				Log.log(Log.ERROR,jEdit.class,"Cannot load"
					+ " plugin " + plugin);
				Log.log(Log.ERROR,jEdit.class,io);
			}
		}
	}

	/**
	 * Adds a plugin to the editor.
	 * @param plugin The plugin
	 */
	public static void addPlugin(EditPlugin plugin)
	{
		plugin.start();
		plugins.addElement(plugin);
	}

	/**
	 * Returns a plugin by it's class name.
	 * @param name The plugin to return
	 */
	public static EditPlugin getPlugin(String name)
	{
		for(int i = 0; i < plugins.size(); i++)
		{
			EditPlugin p = (EditPlugin)plugins.elementAt(i);
			if(p.getClass().getName().equals(name))
				return p;
		}

		return null;
	}

	/**
	 * Returns an array of installed plugins.
	 */
	public static EditPlugin[] getPlugins()
	{
		EditPlugin[] pluginArray = new EditPlugin[plugins.size()];
		plugins.copyInto(pluginArray);
		return pluginArray;
	}

	/**
	 * Returns an array of plugin class names which didn't load.
	 */
	public static EditPlugin.Broken[] getBrokenPlugins()
	{
		EditPlugin.Broken[] pluginArray = new EditPlugin.Broken[brokenPlugins.size()];
		brokenPlugins.copyInto(pluginArray);
		return pluginArray;
	}

	/**
	 * Registers an action with the editor.
	 * @param action The action
	 */
	public static void addAction(EditAction action)
	{
		String name = action.getName();
		actionHash.put(name,new EditAction.Wrapper(action));

		// Register key binding
		String binding = getProperty(name + ".shortcut");
		if(binding != null)
			inputHandler.addKeyBinding(binding,action);
	}

	/**
	 * Returns a named action.
	 * @param action The action
	 */
	public static EditAction getAction(String action)
	{
		return (EditAction)actionHash.get(action);
	}

	/**
	 * Returns the list of actions registered with the editor.
	 */
	public static EditAction[] getActions()
	{
		EditAction[] actions = new EditAction[actionHash.size()];
		Enumeration enum = actionHash.elements();
		int i = 0;
		while(enum.hasMoreElements())
		{
			actions[i++] = (EditAction)enum.nextElement();
		}
		return actions;
	}

	/**
	 * Registers an edit mode with the editor.
	 * @param mode The edit mode
	 */
	public static void addMode(Mode mode)
	{
		modes.addElement(mode);
	}

	/**
	 * Registers an edit mode with the editor.
	 * @param mode The edit mode name
	 */
	public static void addMode(String mode)
	{
		modes.addElement(new Mode(mode));
	}

	/**
	 * Returns the edit mode with the specified name.
	 * @param name The edit mode
	 */
	public static Mode getMode(String name)
	{
		for(int i = 0; i < modes.size(); i++)
		{
			Mode mode = (Mode)modes.elementAt(i);
			if(mode.getName().equals(name))
				return mode;
		}
		return null;
	}

	/**
	 * Returns the localised name of an edit mode.
	 * @param mode The edit mode
	 */
	public static String getModeName(Mode mode)
	{
		return jEdit.props.getProperty("mode." +
			mode.getName() + ".name");
	}

	/**
	 * Returns an array of installed edit modes.
	 */
	public static Mode[] getModes()
	{
		Mode[] array = new Mode[modes.size()];
		modes.copyInto(array);
		return array;
	}

	/**
	 * Opens a file.
	 * @param view The view to open the file in
	 * @param parent The parent directory of the file
	 * @param path The path name of the file
	 * @param readOnly True if the file should be read only
	 * @param newFile True if the file should not be loaded from disk
	 */
	public static Buffer openFile(View view, String parent, String path,
		boolean readOnly, boolean newFile)
	{
		if(view != null && parent == null)
			parent = view.getBuffer().getFile().getParent();

		int index = path.indexOf('#');
		String marker = null;
		if(index != -1)
		{
			marker = path.substring(index + 1);
			path = path.substring(0,index);
		}

		// Java doesn't currently support saving to file:// URLs,
		// hence the crude hack here
		if(path.startsWith("file:"))
			path = path.substring(5);

		URL url = null;
		try
		{
			url = new URL(path);
		}
		catch(MalformedURLException mu)
		{
			path = MiscUtilities.constructPath(parent,path);
		}
		Buffer buffer = buffersFirst;
		while(buffer != null)
		{
			if(buffer.getPath().equals(path))
			{
				if(view != null)
				{
					if(marker != null)
						gotoMarker(buffer,view,marker);
					view.setBuffer(buffer);
				}
				return buffer;
			}
			buffer = buffer.next;
		}

		buffer = new Buffer(url,path,readOnly,newFile,false);
		addBufferToList(buffer);

		EditBus.send(new BufferUpdate(buffer,BufferUpdate.CREATED));

		if(marker != null)
			gotoMarker(buffer,null,marker);

		if(view != null)
			view.setBuffer(buffer);

		return buffer;
	}

	/**
	 * Opens a temporary buffer. A temporary buffer is like a normal
	 * buffer, except that an event is not fired, the the buffer is
	 * not added to the buffers list.
	 *
	 * @param view The view to open the file in
	 * @param parent The parent directory of the file
	 * @param path The path name of the file
	 * @param readOnly True if the file should be read only
	 * @param newFile True if the file should not be loaded from disk
	 */
	public static Buffer openTemporary(View view, String parent,
		String path, boolean readOnly, boolean newFile)
	{
		if(view != null && parent == null)
			parent = view.getBuffer().getFile().getParent();

		// Java doesn't currently support saving to file:// URLs,
		// hence the crude hack here
		if(path.startsWith("file:"))
			path = path.substring(5);

		URL url = null;
		try
		{
			url = new URL(path);
		}
		catch(MalformedURLException mu)
		{
			path = MiscUtilities.constructPath(parent,path);
		}

		Buffer buffer = buffersFirst;
		while(buffer != null)
		{
			if(buffer.getPath().equals(path))
				return buffer;
			buffer = buffer.next;
		}

		return new Buffer(url,path,readOnly,newFile,true);
	}

	/**
	 * Adds a temporary buffer to the buffer list. This must be done
	 * before allowing the user to interact with the buffer in any
	 * way.
	 * @param buffer The buffer
	 */
	public static void commitTemporary(Buffer buffer)
	{
		if(!buffer.isTemporary())
			return;

		buffer.setMode();
		buffer.propertiesChanged();

		addBufferToList(buffer);
		buffer.commitTemporary();

		EditBus.send(new BufferUpdate(buffer,BufferUpdate.CREATED));
	}

	/**
	 * Creates a new `untitled' file.
	 * @param view The view to create the file in
	 */
	public static Buffer newFile(View view)
	{
		// Find the highest Untitled-n file
		int untitledCount = 0;
		Buffer buffer = buffersFirst;
		while(buffer != null)
		{
			if(buffer.getName().startsWith("Untitled-"))
			{
				try
				{
					untitledCount = Math.max(untitledCount,
						Integer.parseInt(buffer.getName()
						.substring(9)));
				}
				catch(NumberFormatException nf)
				{
				}
			}
			buffer = buffer.next;
		}

		return openFile(view,null,"Untitled-" + (untitledCount+1),false,true);
	}

	/**
	 * Closes a buffer. If there are unsaved changes, the user is
	 * prompted if they should be saved first.
	 * @param view The view
	 * @param buffer The buffer
	 * @return True if the buffer was really closed, false otherwise
	 */
	public static boolean closeBuffer(View view, Buffer buffer)
	{
		if(buffer.isDirty())
		{
			Object[] args = { buffer.getName() };
			int result = JOptionPane.showConfirmDialog(view,
				getProperty("notsaved.message",args),
				getProperty("notsaved.title"),
				JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE);
			if(result == JOptionPane.YES_OPTION)
			{
				if(!buffer.save(view,null))
					return false;
			}
			else if(result != JOptionPane.NO_OPTION)
				return false;
		}

		_closeBuffer(view,buffer);
		return true;
	}

	/**
	 * Closes the buffer, even if it has unsaved changes.
	 * @param view The view
	 * @param buffer The buffer
	 *
	 * @since jEdit 2.2pre1
	 */
	public static void _closeBuffer(View view, Buffer buffer)
	{
		removeBufferFromList(buffer);
		buffer.close();

		if(!buffer.isNewFile())
			addRecent(buffer.getPath());

		EditBus.send(new BufferUpdate(buffer,BufferUpdate.CLOSED));

		// Create a new file when the last is closed
		if(buffersFirst == null && buffersLast == null)
			newFile(view);
	}

	/**
	 * Closes all open buffers.
	 * @param view The view
	 * @param isExiting This must be true unless this method is
	 * being called by the exit() method
	 */
	public static boolean closeAllBuffers(View view, boolean isExiting)
	{
		boolean dirty = false;

		Buffer buffer = buffersFirst;
		while(buffer != null)
		{
			if(buffer.isDirty())
			{
				dirty = true;
				break;
			}
			buffer = buffer.next;
		}

		if(dirty)
		{
			boolean ok = new CloseDialog(view).isOK();
			if(!ok)
				return false;
		}

		// close remaining buffers (the close dialog only deals with
		// dirty ones)

		buffer = buffersFirst;

		// zero it here so that BufferTabs doesn't have any problems
		buffersFirst = buffersLast = null;
		bufferCount = 0;

		while(buffer != null)
		{
			if(!buffer.isNewFile())
				addRecent(buffer.getPath());
			buffer.close();
			if(!isExiting)
				EditBus.send(new BufferUpdate(buffer,BufferUpdate.CLOSED));
			buffer = buffer.next;
		}

		if(!isExiting)
			newFile(view);

		return true;
	}

	/**
	 * Returns the buffer with the specified path name. The path name
	 * must be an absolute, canonical, path.
	 * @param path The path name
	 * @see MiscUtilities#constructPath(String,String)
	 */
	public static Buffer getBuffer(String path)
	{
		Buffer buffer = buffersFirst;
		while(buffer != null)
		{
			if(buffer.getPath().equals(path))
				return buffer;
			buffer = buffer.next;
		}
		return null;
	}

	/**
	 * Returns an array of open buffers.
	 */
	public static Buffer[] getBuffers()
	{
		Buffer[] buffers = new Buffer[bufferCount];
		Buffer buffer = buffersFirst;
		for(int i = 0; i < bufferCount; i++)
		{
			buffers[i] = buffer;
			buffer = buffer.next;
		}
		return buffers;
	}

	/**
	 * Returns the number of open buffers.
	 */
	public static int getBufferCount()
	{
		return bufferCount;
	}

	/**
	 * Returns the first buffer.
	 */
	public static Buffer getFirstBuffer()
	{
		return buffersFirst;
	}

	/**
	 * Returns the last buffer.
	 */
	public static Buffer getLastBuffer()
	{
		return buffersLast;
	}

	/**
	 * Returns the current input handler (key binding to action mapping)
	 * @see org.gjt.sp.jedit.textarea.InputHandler
	 */
	public static InputHandler getInputHandler()
	{
		return inputHandler;
	}

	/**
	 * Reloads all key bindings from the properties.
	 * @since 2.3pre1
	 */
	public static void reloadKeyBindings()
	{
		inputHandler.removeAllKeyBindings();

		EditAction[] actions = getActions();
		for(int i = 0; i < actions.length; i++)
		{
			EditAction action = actions[i];
			String shortcut = jEdit.getProperty(action.getName()
				+ ".shortcut");
			if(shortcut != null)
				inputHandler.addKeyBinding(shortcut,action);
		}

		// load text area bindings
		initKeyBindings();

		// load mode-specific keys
		Mode[] modes = getModes();
		for(int i = 0; i < modes.length; i++)
		{
			modes[i].initKeyBindings();
		}
	}

	/**
	 * Creates a new view of a buffer.
	 * @param view The view from which to take the geometry, buffer and
	 * caret position from
	 * @param buffer The buffer
	 */
	public static View newView(View view, Buffer buffer)
	{
		if(view != null)
		{
			view.showWaitCursor();
			view.saveCaretInfo();
		}

		View newView = new View(view,buffer);
		addViewToList(newView);

		EditBus.send(new ViewUpdate(newView,ViewUpdate.CREATED));

		// Do this crap here so that the view is created
		// and added to the list before it is shown
		// (for the sake of plugins that add stuff to views)
		newView.pack();

		if(view != null)
		{
			newView.setSize(view.getSize());
			Point location = view.getLocation();
			location.x += 20;
			location.y += 20;
			newView.setLocation(location);

			view.hideWaitCursor();
		}
		else
		{
			GUIUtilities.loadGeometry(newView,"view");
		}

		newView.show();
		newView.focusOnTextArea();

		return newView;
	}

	/**
	 * Closes a view. jEdit will exit if this was the last open view.
	 */
	public static void closeView(View view)
	{
		if(viewsFirst == viewsLast)
			exit(view); /* exit does editor event & save */
		else
		{
			EditBus.send(new ViewUpdate(view,ViewUpdate.CLOSED));

			view.close();
			removeViewFromList(view);
		}
	}

	/**
	 * Returns an array of all open views.
	 */
	public static View[] getViews()
	{
		View[] views = new View[viewCount];
		View view = viewsFirst;
		for(int i = 0; i < viewCount; i++)
		{
			views[i] = view;
			view = view.next;
		}
		return views;
	}

	/**
	 * Returns the number of open views.
	 */
	public static int getViewCount()
	{
		return viewCount;
	}

	/**
	 * Returns the first view.
	 */
	public static View getFirstView()
	{
		return viewsFirst;
	}

	/**
	 * Returns the last view.
	 */
	public static View getLastView()
	{
		return viewsLast;
	}

	/**
	 * Returns an array of recently opened files.
	 */
	public static String[] getRecent()
	{
		String[] array = new String[recent.size()];
		recent.copyInto(array);
		return array;
	}

	/**
	 * Returns the jEdit install directory.
	 */
	public static String getJEditHome()
	{
		return jEditHome;
	}

	/**
	 * Returns the user settings directory.
	 */
	public static String getSettingsDirectory()
	{
		return settingsDirectory;
	}

	/**
	 * Exits cleanly from jEdit, prompting the user if any unsaved files
	 * should be saved first.
	 * @param view The view from which this exit was called
	 */
	public static void exit(View view)
	{
		if(settingsDirectory != null && session != null)
			Sessions.saveSession(view,session);

		// Close all buffers
		if(!closeAllBuffers(view,true))
			return;

		// Stop autosave thread
		autosave.stop();

		// Stop server here
		if(server != null)
			server.stopServer();

		// Save view properties here - save unregisters
		// listeners, and we would have problems if the user
		// closed a view but cancelled an unsaved buffer close
		view.close();

		// Stop all plugins
		for(int i = 0; i < plugins.size(); i++)
		{
			((EditPlugin)plugins.elementAt(i)).stop();
		}

		// Send EditorExiting
		EditBus.send(new EditorExiting(null));

		// Save various settings
		if(settingsDirectory != null)
		{
			// Save the recent file list
			for(int i = 0; i < recent.size(); i++)
			{
				String file = (String)recent.elementAt(i);
				setProperty("recent." + i,file);
			}
			unsetProperty("recent." + maxRecent);

			HistoryModel.saveHistory(MiscUtilities.constructPath(
				settingsDirectory, "history"));

			SearchAndReplace.save();
			Abbrevs.save();

			File file = new File(MiscUtilities.constructPath(
				settingsDirectory,"properties"));
			if(file.lastModified() > propsModTime)
			{
				Log.log(Log.WARNING,jEdit.class,file + " changed"
					+ " on disk; will not save user properties");
			}
			else
			{
				try
				{
					OutputStream out = new FileOutputStream(file);
					props.save(out,"jEdit properties");
					out.close();
				}
				catch(IOException io)
				{
					Log.log(Log.ERROR,jEdit.class,io);
				}
			}
		}

		// Byebye...
		System.exit(0);
	}

	// package-private members

	/**
	 * If buffer sorting is enabled, this repositions the buffer.
	 */
	static void updatePosition(Buffer buffer)
	{
		if(sortBuffers)
		{
			removeBufferFromList(buffer);
			addBufferToList(buffer);
		}
	}

	/**
	 * This plugin didn't load.
	 */
	static void addBrokenPlugin(String jar, String name)
	{
		brokenPlugins.addElement(new EditPlugin.Broken(jar,name));
	}

	// private members
	private static String jEditHome;
	private static String settingsDirectory;
	private static long propsModTime;
	private static String session;
	private static Properties defaultProps;
	private static Properties props;
	private static Autosave autosave;
	private static EditServer server;
	private static Hashtable actionHash;
	private static Vector plugins;
	private static Vector brokenPlugins;
	private static Vector modes;
	private static Vector recent;
	private static int maxRecent;
	private static InputHandler inputHandler;

	// buffer link list
	private static boolean sortBuffers;
	private static boolean sortByName;
	private static int bufferCount;
	private static Buffer buffersFirst;
	private static Buffer buffersLast;

	// view link list
	private static int viewCount;
	private static View viewsFirst;
	private static View viewsLast;

	private jEdit() {}

	private static void usage()
	{
		System.out.println("Usage: jedit [<options>] [<files>]");

		System.out.println("Common options:");
		System.out.println("	<filename>#<marker>: Positions caret"
			+ " at marker <marker>");
		System.out.println("	<filename>#+<line>: Positions caret"
			+ " at line number <line>");
		System.out.println("	--: End of options");
		System.out.println("	-version: Print jEdit version and"
			+ " exit");
		System.out.println("	-usage: Print this message and exit");
		System.out.println("	-readonly: Open files read-only");
		System.out.println("	-nosession: Don't load default session");
		System.out.println("	-session=<name>: Load session from"
			+ " $HOME/.jedit/sessions/<name>");
		System.out.println("	-noserver: Don't start editor server");
		System.out.println("	-server=<name>: Reads/writes server"
			+ " info to $HOME/.jedit/<name>");

		System.out.println();
		System.out.println("Server-only options:");
		System.out.println("	-nosettings: Don't load user-specific"
			+ " settings");
		System.out.println("	-settings=<path>: Load user-specific"
			+ " settings from <path>");
		System.out.println("	-nosplash: Don't show splash screen");
		System.out.println();
		System.out.println("Client-only options:");
		System.out.println("	-reuseview: Don't open new view in");

		System.out.println();
		System.out.println("To set minimum activity log level,"
			+ " specify a number as the first");
		System.out.println("command line parameter"
			+ " (1-9, 1 = debug, 9 = error)");
		System.out.println();
		System.out.println("Report bugs to Slava Pestov <sp@gjt.org>.");
	}

	private static void version()
	{
		System.out.println("jEdit " + getVersion());
	}

	/**
	 * Initialise various objects, register protocol handlers,
	 * register editor listener, and determine installation
	 * directory.
	 */
	private static void initMisc()
	{
		Log.log(Log.NOTICE,jEdit.class,"jEdit version " + getVersion());
		Log.log(Log.MESSAGE,jEdit.class,"Settings directory is "
			+ settingsDirectory);

		inputHandler = new DefaultInputHandler();

		// Add our protocols to java.net.URL's list
		System.getProperties().put("java.protocol.handler.pkgs",
			"org.gjt.sp.jedit.proto|" +
			System.getProperty("java.protocol.handler.pkgs",""));

		// Determine installation directory
		jEditHome = System.getProperty("jedit.home");
		if(jEditHome == null)
		{
			String classpath = System
				.getProperty("java.class.path");
			int index = classpath.toLowerCase()
				.indexOf("jedit.jar");
			int start = classpath.lastIndexOf(File
				.pathSeparator,index) + 1;
			if(index > start)
			{
				jEditHome = classpath.substring(start,
					index - 1);
			}
			else
				jEditHome = System.getProperty("user.dir");
		}

		if(settingsDirectory != null)
		{
			File _settingsDirectory = new File(settingsDirectory);
			if(!_settingsDirectory.exists())
				_settingsDirectory.mkdirs();
			File _macrosDirectory = new File(settingsDirectory,"macros");
			if(!_macrosDirectory.exists())
				_macrosDirectory.mkdir();
			File _sessionsDirectory = new File(settingsDirectory,"sessions");
			if(!_sessionsDirectory.exists())
				_sessionsDirectory.mkdir();
		}
	}

	/**
	 * Load system properties.
	 */
	private static void initSystemProperties()
	{
		defaultProps = props = new Properties();

		try
		{
			loadProps(jEdit.class.getResourceAsStream(
				"/org/gjt/sp/jedit/jedit.props"));
			loadProps(jEdit.class.getResourceAsStream(
				"/org/gjt/sp/jedit/jedit_gui.props"));
			loadProps(jEdit.class.getResourceAsStream(
				"/org/gjt/sp/jedit/jedit_keys.props"));
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,jEdit.class,
				"Error while loading system properties!");
			Log.log(Log.ERROR,jEdit.class,
				"One of the following property files could not be loaded:\n"
				+ "- jedit.props\n"
				+ "- jedit_gui.props\n"
				+ "- jedit_keys.props\n"
				+ "Try reinstalling jEdit.");
			Log.log(Log.ERROR,jEdit.class,e);
			System.exit(1);
		}
	}

	/**
	 * Load edit modes.
	 */
	private static void initModes()
	{
		/* Try to guess the eventual size to avoid unnecessary
		 * copying */
		modes = new Vector(19 /* modes built into jEdit */
			+ 10 /* give plugins some space */);

		addMode("bat");
		addMode("bsh");
		addMode("c");
		addMode("cc");
		addMode("eiffel");
		addMode("html");
		addMode("idl");
		addMode("java");
		addMode("javascript");
		addMode("makefile");
		addMode("patch");
		addMode("perl");
		addMode("php3");
		addMode("postscript");
		addMode("props");
		addMode("python");
		addMode("shell");
		addMode("tex");
		addMode("text");
		addMode("tsql");
		addMode("xml");
	}

	/**
	 * Registers an action with the editor. This is for internal
	 * use only.
	 * @param action The action
	 */
	private static void addAction(String name)
	{
		EditAction.Wrapper action = new EditAction.Wrapper(name);
		actionHash.put(name,action);

		// Register key binding
		String binding = getProperty(name + ".shortcut");
		if(binding != null)
			inputHandler.addKeyBinding(binding,action);
	}

	/**
	 * Load actions.
	 */
	private static void initActions()
	{
		actionHash = new Hashtable();

		addAction("about");
		addAction("append-string-register");
		addAction("block-comment");
		addAction("box-comment");
		addAction("buffer-options");
		addAction("clear-log");
		addAction("clear-marker");
		addAction("clear-register");
		addAction("close-all");
		addAction("close-file");
		addAction("close-view");
		addAction("complete-word");
		addAction("copy");
		addAction("copy-string-register");
		addAction("cut");
		addAction("cut-string-register");
		addAction("delete-end-line");
		addAction("delete-line");
		addAction("delete-paragraph");
		addAction("delete-start-line");
		addAction("edit-macro");
		addAction("exchange-caret-register");
		addAction("exit");
		addAction("expand-abbrev");
		addAction("find");
		addAction("find-next");
		addAction("find-selection");
		addAction("global-options");
		addAction("goto-end-indent");
		addAction("goto-line");
		addAction("goto-marker");
		addAction("goto-register");
		addAction("help");
		addAction("hypersearch");
		addAction("hypersearch-selection");
		addAction("indent-lines");
		addAction("indent-on-enter");
		addAction("indent-on-tab");
		addAction("insert-char");
		addAction("insert-literal");
		addAction("join-lines");
		addAction("load-session");
		addAction("locate-bracket");
		addAction("log-viewer");
		addAction("new-file");
		addAction("new-view");
		addAction("next-bracket-exp");
		addAction("next-buffer");
		addAction("next-paragraph");
		addAction("open-file");
		addAction("open-path");
		addAction("open-url");
		addAction("paste");
		addAction("paste-previous");
		addAction("paste-string-register");
		addAction("play-last-macro");
		addAction("play-temp-macro");
		addAction("play-macro");
		addAction("prev-bracket-exp");
		addAction("prev-buffer");
		addAction("prev-paragraph");
		addAction("print");
		addAction("record-macro");
		addAction("record-temp-macro");
		addAction("redo");
		addAction("reload");
		addAction("reload-all");
		addAction("replace-all");
		addAction("replace-in-selection");
		addAction("rescan-macros");
		addAction("save");
		addAction("save-all");
		addAction("save-as");
		addAction("save-gutter-size");
		addAction("save-log");
		addAction("save-session");
		addAction("scroll-line");
		addAction("search-and-replace");
		addAction("select-all");
		addAction("select-block");
		addAction("select-buffer");
		addAction("select-caret-register");
		addAction("select-line");
		addAction("select-line-range");
		addAction("select-next-paragraph");
		addAction("select-none");
		addAction("select-paragraph");
		addAction("select-prev-paragraph");
		addAction("select-word");
		addAction("set-caret-register");
		addAction("set-filename-register");
		addAction("set-replace-string");
		addAction("set-search-parameters");
		addAction("set-search-string");
		addAction("send");
		addAction("set-marker");
		addAction("shift-left");
		addAction("shift-right");
		addAction("split-horizontal");
		addAction("split-vertical");
		addAction("stop-recording");
		addAction("tab");
		addAction("to-lower");
		addAction("to-upper");
		addAction("undo");
		addAction("unsplit");
		addAction("untab");
		addAction("view-registers");
		addAction("wing-comment");
		addAction("word-count");

		// this is the default action. We override the text area's
		// one to handle abbrev expansion
		inputHandler.setInputAction(getAction("insert-char"));

		// Preload these actions so that isToggle()
		// will always return the correct value
		addAction(new org.gjt.sp.jedit.actions.ignore_case());
		addAction(new org.gjt.sp.jedit.actions.multifile_search());
		addAction(new org.gjt.sp.jedit.actions.regexp());
		addAction(new org.gjt.sp.jedit.actions.toggle_gutter());
		addAction(new org.gjt.sp.jedit.actions.toggle_line_numbers());
		addAction(new org.gjt.sp.jedit.actions.toggle_rect());
	}

	/**
	 * Loads plugins.
	 */
	private static void initPlugins()
	{
		plugins = new Vector();
		brokenPlugins = new Vector();
		loadPlugins(MiscUtilities.constructPath(jEditHome,"jars"));
		if(settingsDirectory != null)
		{
			File jarsDirectory = new File(settingsDirectory,"jars");
			if(!jarsDirectory.exists())
				jarsDirectory.mkdir();
			loadPlugins(jarsDirectory.getPath());
		}
	}

	/**
	 * Loads user properties.
	 */
	private static void initUserProperties()
	{
		props = new Properties(defaultProps);

		if(settingsDirectory != null)
		{
			File file = new File(MiscUtilities.constructPath(
				settingsDirectory,"properties"));
			propsModTime = file.lastModified();

			try
			{
				loadProps(new FileInputStream(file));
			}
			catch(FileNotFoundException fnf)
			{
				Log.log(Log.DEBUG,jEdit.class,fnf);
			}
			catch(IOException e)
			{
				Log.log(Log.ERROR,jEdit.class,e);
			}
		}
	}

	/**
	 * Sets the Swing look and feel.
	 */
	private static void initPLAF()
	{
		String lf = getProperty("lookAndFeel");
		try
		{
			if(lf != null && lf.length() != 0)
				UIManager.setLookAndFeel(lf);
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,jEdit.class,e);
		}
	}

	/**
	 * Loads the recent file list.
	 */
	private static void initRecent()
	{
		recent = new Vector(maxRecent);

		for(int i = 0; i < maxRecent; i++)
		{
			String recentFile = getProperty("recent." + i);
			if(recentFile != null)
				recent.addElement(recentFile);
		}
	}

	/**
	 * Loads the text area key bindings.
	 */
	private static void initKeyBindings()
	{
		Enumeration textActions = InputHandler.getActions();
		while(textActions.hasMoreElements())
		{
			String name = (String)textActions.nextElement();
			String binding = jEdit.getProperty(
				name + ".shortcut");
			if(binding != null)
			{
				inputHandler.addKeyBinding(binding,
					InputHandler.getAction(name));
			}
		}
	}

	private static void gotoMarker(Buffer buffer, View view, String marker)
	{
		if(marker.length() == 0)
			return;

		// load buffer if necessary
		buffer.loadIfNecessary(view);

		int start, end;

		// Handle line number
		if(marker.charAt(0) == '+')
		{
			try
			{
				int line = Integer.parseInt(marker.substring(1));
				Element lineElement = buffer.getDefaultRootElement()
					.getElement(line + 1);
				start = end = lineElement.getStartOffset();
			}
			catch(Exception e)
			{
				return;
			}
		}
		// Handle marker
		else
		{
			Marker m = buffer.getMarker(marker);
			if(m == null)
				return;
			start = m.getStart();
			end = m.getEnd();
		}

		if(view == null || view.getBuffer() != buffer)
		{
			buffer.putProperty(Buffer.SELECTION_START,new Integer(start));
			buffer.putProperty(Buffer.SELECTION_END,new Integer(end));
		}
		else if(view != null)
			view.getTextArea().select(start,end);
	}

	private static void addBufferToList(Buffer buffer)
	{
		bufferCount++;

		if(buffersFirst == null)
		{
			buffersFirst = buffersLast = buffer;
			return;
		}
		else if(sortBuffers)
		{
			String name1 = (sortByName ? buffer.getName()
				: buffer.getPath()).toLowerCase();

			Buffer _buffer = buffersFirst;
			while(_buffer != null)
			{
				String name2 = (sortByName ? _buffer.getName()
					: _buffer.getPath()).toLowerCase();
				if(name1.compareTo(name2) <= 0)
				{
					buffer.next = _buffer;
					buffer.prev = _buffer.prev;
					_buffer.prev = buffer;
					if(_buffer != buffersFirst)
						buffer.prev.next = buffer;
					else
						buffersFirst = buffer;
					return;
				}

				_buffer = _buffer.next;
			}
		}

		buffer.prev = buffersLast;
		buffersLast.next = buffer;
		buffersLast = buffer;
	}

	private static void removeBufferFromList(Buffer buffer)
	{
		bufferCount--;

		if(buffer == buffersFirst && buffer == buffersLast)
		{
			buffersFirst = buffersLast = null;
			return;
		}

		if(buffer == buffersFirst)
		{
			buffersFirst = buffer.next;
			buffer.next.prev = null;
		}
		else
		{
			buffer.prev.next = buffer.next;
		}

		if(buffer == buffersLast)
		{
			buffersLast = buffersLast.prev;
			buffer.prev.next = null;
		}
		else
		{
			buffer.next.prev = buffer.prev;
		}
	}

	private static void addViewToList(View view)
	{
		viewCount++;

		if(viewsFirst == null)
			viewsFirst = viewsLast = view;
		else
		{
			view.prev = viewsLast;
			viewsLast.next = view;
			viewsLast = view;
		}
	}

	private static void removeViewFromList(View view)
	{
		viewCount--;

		if(view == viewsFirst)
		{
			viewsFirst = view.next;
			view.next.prev = null;
		}
		else
		{
			view.prev.next = view.next;
		}

		if(view == viewsLast)
		{
			viewsLast = viewsLast.prev;
			view.prev.next = null;
		}
		else
		{
			view.next.prev = view.prev;
		}
	}

	private static void addRecent(String path)
	{
		if(recent.contains(path))
			recent.removeElement(path);
		recent.insertElementAt(path,0);
		if(recent.size() > maxRecent)
			recent.removeElementAt(maxRecent);
	}
}

/*
 * ChangeLog:
 * $Log$
 * Revision 1.193  2000/02/24 04:13:06  sp
 * Bug fixes, misc updates, etc
 *
 * Revision 1.192  2000/02/20 03:14:13  sp
 * jEdit.getBrokenPlugins() method
 *
 * Revision 1.191  2000/02/15 07:44:30  sp
 * bug fixes, doc updates, etc
 *
 * Revision 1.190  2000/02/07 06:35:53  sp
 * Options dialog box updates
 *
 * Revision 1.189  2000/02/04 05:50:27  sp
 * More gutter updates from mike
 *
 * Revision 1.188  2000/02/02 06:23:44  sp
 * Gutter changes from mike
 *
 * Revision 1.187  2000/01/29 10:12:43  sp
 * BeanShell edit mode, bug fixes
 *
 * Revision 1.186  2000/01/29 08:18:08  sp
 * bug fixes, misc updates
 *
 * Revision 1.185  2000/01/29 03:27:20  sp
 * Split window functionality added
 *
 * Revision 1.184  2000/01/28 09:24:16  sp
 * Buffer tabs updated (uses better impl == less bugs)
 *
 * Revision 1.183  2000/01/28 00:25:47  sp
 * Minor tweak
 *
 * Revision 1.182  2000/01/28 00:20:58  sp
 * Lots of stuff
 *
 */
