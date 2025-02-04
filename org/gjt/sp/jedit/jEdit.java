/*
 * jEdit.java - Main class of the jEdit editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 1998, 1999, 2000, 2001, 2002 Slava Pestov
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

//{{{ Imports
import com.microstar.xml.*;
import javax.swing.plaf.metal.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.MessageFormat;
import java.util.*;
import org.gjt.sp.jedit.browser.VFSBrowser;
import org.gjt.sp.jedit.msg.*;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.io.*;
import org.gjt.sp.jedit.search.SearchAndReplace;
import org.gjt.sp.jedit.syntax.*;
import org.gjt.sp.jedit.textarea.*;
import org.gjt.sp.util.Log;
//}}}

/**
 * The main class of the jEdit text editor.
 * @author Slava Pestov
 * @version $Id$
 */
public class jEdit
{
	//{{{ getVersion() method
	/**
	 * Returns the jEdit version as a human-readable string.
	 */
	public static String getVersion()
	{
		return MiscUtilities.buildToVersion(getBuild());
	} //}}}

	//{{{ getBuild() method
	/**
	 * Returns the internal version. MiscUtilities.compareStrings() can be used
	 * to compare different internal versions.
	 */
	public static String getBuild()
	{
		// (major).(minor).(<99 = preX, 99 = final).(bug fix)
		return "04.00.99.03";
	} //}}}

	//{{{ main() method
	/**
	 * The main method of the jEdit application.
	 * This should never be invoked directly.
	 * @param args The command line arguments
	 */
	public static void main(String[] args)
	{
		String javaVersion = System.getProperty("java.version");
		if(javaVersion.compareTo("1.3") < 0)
		{
			System.err.println("You are running Java version "
				+ javaVersion + ".");
			System.err.println("jEdit requires Java 1.3 or later.");
			System.exit(1);
		}

		//{{{ Parse command line
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

		boolean endOpts = false;
		settingsDirectory = MiscUtilities.constructPath(
			System.getProperty("user.home"),".jedit");
		String portFile = "server";
		boolean restore = true;
		boolean gui = true; // open initial view?
		boolean noPlugins = false;
		boolean noStartupScripts = false;
		String userDir = System.getProperty("user.dir");

		// script to run
		String scriptFile = null;

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
				else if(arg.equals("-server"))
					portFile = "server";
				else if(arg.startsWith("-server="))
					portFile = arg.substring(8);
				else if(arg.startsWith("-background"))
					background = true;
				else if(arg.equals("-nogui"))
					gui = false;
				else if(arg.equals("-norestore"))
					restore = false;
				else if(arg.equals("-noplugins"))
					noPlugins = true;
				else if(arg.equals("-nostartupscripts"))
					noStartupScripts = true;
				else if(arg.startsWith("-run="))
					scriptFile = arg.substring(5);
				else
				{
					System.err.println("Unknown option: "
						+ arg);
					usage();
					System.exit(1);
				}
				args[i] = null;
			}
		} //}}}

		if(settingsDirectory != null && portFile != null)
			portFile = MiscUtilities.constructPath(settingsDirectory,portFile);
		else
			portFile = null;

		Log.init(true,level);

		//{{{ Try connecting to another running jEdit instance
		if(portFile != null && new File(portFile).exists())
		{
			int port, key;
			try
			{
				BufferedReader in = new BufferedReader(new FileReader(portFile));
				String check = in.readLine();
				if(!check.equals("b"))
					throw new Exception("Wrong port file format");

				port = Integer.parseInt(in.readLine());
				key = Integer.parseInt(in.readLine());
				in.close();

				Socket socket = new Socket(InetAddress.getByName("127.0.0.1"),port);
				DataOutputStream out = new DataOutputStream(
					socket.getOutputStream());
				out.writeInt(key);

				String script = makeServerScript(restore,args,scriptFile);

				out.writeUTF(script);

				out.close();

				System.exit(0);
			}
			catch(Exception e)
			{
				// ok, this one seems to confuse newbies
				// endlessly, so log it as NOTICE, not
				// ERROR
				Log.log(Log.NOTICE,jEdit.class,"An error occurred"
					+ " while connecting to the jEdit server instance.");
				Log.log(Log.NOTICE,jEdit.class,"This probably means that"
					+ " jEdit crashed and/or exited abnormally");
				Log.log(Log.NOTICE,jEdit.class,"the last time it was run.");
				Log.log(Log.NOTICE,jEdit.class,"If you don't"
					+ " know what this means, don't worry.");
				Log.log(Log.NOTICE,jEdit.class,e);
			}
		} //}}}

		// don't show splash screen if there is a file named
		// 'nosplash' in the settings directory
		if(!new File(settingsDirectory,"nosplash").exists())
			GUIUtilities.showSplashScreen();

		//{{{ Initialize settings directory
		Writer stream;
		if(settingsDirectory != null)
		{
			File _settingsDirectory = new File(settingsDirectory);
			if(!_settingsDirectory.exists())
				_settingsDirectory.mkdirs();
			File _macrosDirectory = new File(settingsDirectory,"macros");
			if(!_macrosDirectory.exists())
				_macrosDirectory.mkdir();

			String logPath = MiscUtilities.constructPath(
				settingsDirectory,"activity.log");

			backupSettingsFile(new File(logPath));

			try
			{
				stream = new BufferedWriter(new FileWriter(logPath));
			}
			catch(Exception e)
			{
				e.printStackTrace();
				stream = null;
			}
		}
		else
		{
			stream = null;
		} //}}}

		Log.setLogWriter(stream);

		Log.log(Log.NOTICE,jEdit.class,"jEdit version " + getVersion());
		Log.log(Log.MESSAGE,jEdit.class,"Settings directory is "
			+ settingsDirectory);

		//{{{ Initialize server
		if(portFile != null)
		{
			server = new EditServer(portFile);
			if(!server.isOK())
				server = null;
		}
		else
		{
			if(background)
			{
				background = false;
				System.err.println("You cannot specify both the"
					+ " -background and -noserver switches");
			}
		} //}}}

		//{{{ Get things rolling
		initMisc();
		initSystemProperties();
		if(jEditHome != null)
			initSiteProperties();
		GUIUtilities.advanceSplashProgress();

		BeanShell.init();

		initUserProperties();
		initPLAF();

		if(OperatingSystem.hasJava14()
			&& System.getProperty("jedit.nojava14") == null)
		{
			try
			{
				ClassLoader loader = jEdit.class.getClassLoader();
				Class clazz;
				if(loader != null)
					clazz = loader.loadClass("org.gjt.sp.jedit.Java14");
				else
					clazz = Class.forName("org.gjt.sp.jedit.Java14");
				java.lang.reflect.Method meth = clazz
					.getMethod("init",new Class[0]);
				meth.invoke(null,new Object[0]);
			}
			catch(Exception e)
			{
				Log.log(Log.ERROR,jEdit.class,e);
				System.exit(1);
			}
		}

		initActions();
		initDockables();

		GUIUtilities.advanceSplashProgress();

		VFSManager.init();

		if(!noPlugins)
			initPlugins();

		if(settingsDirectory != null)
		{
			File history = new File(MiscUtilities.constructPath(
				settingsDirectory,"history"));
			if(history.exists())
				historyModTime = history.lastModified();
			HistoryModel.loadHistory(history);

			File recent = new File(MiscUtilities.constructPath(
				settingsDirectory,"recent.xml"));
			if(recent.exists())
				recentModTime = recent.lastModified();
			BufferHistory.load(recent);
		}

		GUIUtilities.advanceSplashProgress();

		// Buffer sort
		sortBuffers = getBooleanProperty("sortBuffers");
		sortByName = getBooleanProperty("sortByName");

		reloadModes();

		GUIUtilities.advanceSplashProgress();

		SearchAndReplace.load();

		GUIUtilities.advanceSplashProgress();
		//}}}

		//{{{ Start plugins
		for(int i = 0; i < jars.size(); i++)
		{
			((EditPlugin.JAR)jars.elementAt(i)).getClassLoader()
				.startAllPlugins();
		} //}}}

		//{{{ Load macros and run startup scripts, after plugins and settings are loaded
		Macros.loadMacros();

		if(!noStartupScripts && jEditHome != null)
		{
			String path = MiscUtilities.constructPath(jEditHome,"startup");
			File file = new File(path);
			if(file.exists())
				runStartupScripts(file);
		}

		if(!noStartupScripts && settingsDirectory != null)
		{
			String path = MiscUtilities.constructPath(settingsDirectory,"startup");
			File file = new File(path);
			if(!file.exists())
				file.mkdirs();
			else
				runStartupScripts(file);
		} //}}}

		//{{{ Run script specified with -run= parameter
		if(scriptFile != null)
		{
			scriptFile = MiscUtilities.constructPath(userDir,scriptFile);
			BeanShell.runScript(null,scriptFile,null,false);
		} //}}}

		// Must be after plugins are started!!!
		propertiesChanged();

		GUIUtilities.advanceSplashProgress();

		//{{{ Open files
		Buffer buffer = openFiles(null,userDir,args);
		if(buffer != null)
		{
			// files specified on command line; force initial view
			// to open
			gui = true;
		}

		String splitConfig = null;

		if(restore && settingsDirectory != null
			&& jEdit.getBooleanProperty("restore")
			&& (bufferCount == 0 || jEdit.getBooleanProperty("restore.cli")))
		{
			splitConfig = restoreOpenFiles();
		}

		if(bufferCount == 0 && gui)
			newFile(null);
		//}}}

		//{{{ Create the view and hide the splash screen.
		final Buffer _buffer = buffer;
		final String _splitConfig = splitConfig;
		final boolean _gui = gui;

		GUIUtilities.advanceSplashProgress();

		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				EditBus.send(new EditorStarted(null));

				if(_gui)
				{
					View view;
					if(_buffer != null)
						view = newView(null,_buffer);
					else
						view = newView(null,_splitConfig);
				}

				// Start I/O threads
				VFSManager.start();

				// Start edit server
				if(server != null)
					server.start();

				GUIUtilities.hideSplashScreen();

				Log.log(Log.MESSAGE,jEdit.class,"Startup "
					+ "complete");

				//{{{ Report any plugin errors
				if(pluginErrors != null)
				{
					String caption = jEdit.getProperty(
						"plugin-error.caption" + (pluginErrors.size() == 1
						? "-1" : ""),new Integer[] {
						new Integer(pluginErrors.size()) });

					new ErrorListDialog(
						jEdit.getFirstView(),
						jEdit.getProperty("plugin-error.title"),
						caption,pluginErrors,true);
					pluginErrors.removeAllElements();
				} //}}}

				// in one case not a single AWT class will
				// have been touched (splash screen off +
				// -nogui -nobackground switches on command
				// line)
				Toolkit.getDefaultToolkit();
			}
		}); //}}}
	} //}}}

	//{{{ Property methods

	//{{{ getProperties() method
	/**
	 * Returns the properties object which contains all known
	 * jEdit properties.
	 * @since jEdit 3.1pre4
	 */
	public static final Properties getProperties()
	{
		return props;
	} //}}}

	//{{{ getProperty() method
	/**
	 * Fetches a property, returning null if it's not defined.
	 * @param name The property
	 */
	public static final String getProperty(String name)
	{
		return props.getProperty(name);
	} //}}}

	//{{{ getProperty() method
	/**
	 * Fetches a property, returning the default value if it's not
	 * defined.
	 * @param name The property
	 * @param def The default value
	 */
	public static final String getProperty(String name, String def)
	{
		return props.getProperty(name,def);
	} //}}}

	//{{{ getProperty() method
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
			return props.getProperty(name);
		else
		{
			String value = props.getProperty(name);
			if(value == null)
				return null;
			else
				return MessageFormat.format(value,args);
		}
	} //}}}

	//{{{ getBooleanProperty() method
	/**
	 * Returns the value of a boolean property.
	 * @param name The property
	 */
	public static final boolean getBooleanProperty(String name)
	{
		return getBooleanProperty(name,false);
	} //}}}

	//{{{ getBooleanProperty() method
	/**
	 * Returns the value of a boolean property.
	 * @param name The property
	 * @param def The default value
	 */
	public static final boolean getBooleanProperty(String name, boolean def)
	{
		String value = getProperty(name);
		if(value == null)
			return def;
		else if(value.equals("true") || value.equals("yes")
			|| value.equals("on"))
			return true;
		else if(value.equals("false") || value.equals("no")
			|| value.equals("off"))
			return false;
		else
			return def;
	} //}}}

	//{{{ getIntegerProperty() method
	/**
	 * Returns the value of an integer property.
	 * @param name The property
	 * @param def The default value
	 * @since jEdit 4.0pre1
	 */
	public static final int getIntegerProperty(String name, int def)
	{
		String value = getProperty(name);
		if(value == null)
			return def;
		else
		{
			try
			{
				return Integer.parseInt(value);
			}
			catch(NumberFormatException nf)
			{
				return def;
			}
		}
	} //}}}

	//{{{ getFontProperty() method
	/**
	 * Returns the value of a font property. The family is stored
	 * in the <code><i>name</i></code> property, the font size is stored
	 * in the <code><i>name</i>size</code> property, and the font style is
	 * stored in <code><i>name</i>style</code>. For example, if
	 * <code><i>name</i></code> is <code>view.gutter.font</code>, the
	 * properties will be named <code>view.gutter.font</code>,
	 * <code>view.gutter.fontsize</code>, and
	 * <code>view.gutter.fontstyle</code>.
	 *
	 * @param name The property
	 * @since jEdit 4.0pre1
	 */
	public static final Font getFontProperty(String name)
	{
		return getFontProperty(name,null);
	} //}}}

	//{{{ getFontProperty() method
	/**
	 * Returns the value of a font property. The family is stored
	 * in the <code><i>name</i></code> property, the font size is stored
	 * in the <code><i>name</i>size</code> property, and the font style is
	 * stored in <code><i>name</i>style</code>. For example, if
	 * <code><i>name</i></code> is <code>view.gutter.font</code>, the
	 * properties will be named <code>view.gutter.font</code>,
	 * <code>view.gutter.fontsize</code>, and
	 * <code>view.gutter.fontstyle</code>.
	 *
	 * @param name The property
	 * @param def The default value
	 * @since jEdit 4.0pre1
	 */
	public static final Font getFontProperty(String name, Font def)
	{
		String family = getProperty(name);
		String sizeString = getProperty(name + "size");
		String styleString = getProperty(name + "style");

		if(family == null || sizeString == null || styleString == null)
			return def;
		else
		{
			int size, style;

			try
			{
				size = Integer.parseInt(sizeString);
			}
			catch(NumberFormatException nf)
			{
				return def;
			}

			try
			{
				style = Integer.parseInt(styleString);
			}
			catch(NumberFormatException nf)
			{
				return def;
			}

			return new Font(family,style,size);
		}
	} //}}}

	//{{{ getColorProperty() method
	/**
	 * Returns the value of a color property.
	 * @param name The property name
	 * @since jEdit 4.0pre1
	 */
	public static Color getColorProperty(String name)
	{
		return getColorProperty(name,Color.black);
	} //}}}

	//{{{ getColorProperty() method
	/**
	 * Returns the value of a color property.
	 * @param name The property name
	 * @param def The default value
	 * @since jEdit 4.0pre1
	 */
	public static Color getColorProperty(String name, Color def)
	{
		String value = getProperty(name);
		if(value == null)
			return def;
		else
			return GUIUtilities.parseColor(value,def);
	} //}}}

	//{{{ setColorProperty() method
	/**
	 * Sets the value of a color property.
	 * @param name The property name
	 * @param value The value
	 * @since jEdit 4.0pre1
	 */
	public static void setColorProperty(String name, Color value)
	{
		setProperty(name,GUIUtilities.getColorHexString(value));
	} //}}}

	//{{{ setProperty() method
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
			String prop = (String)defaultProps.get(name);
			if(value.equals(prop))
				props.remove(name);
			else
				props.put(name,value);
		}
	} //}}}

	//{{{ setTemporaryProperty() method
	/**
	 * Sets a property to a new value. Properties set using this
	 * method are not saved to the user properties list.
	 * @param name The property
	 * @param value The new value
	 * @since jEdit 2.3final
	 */
	public static final void setTemporaryProperty(String name, String value)
	{
		props.remove(name);
		defaultProps.put(name,value);
	} //}}}

	//{{{ setBooleanProperty() method
	/**
	 * Sets a boolean property.
	 * @param name The property
	 * @param value The value
	 */
	public static final void setBooleanProperty(String name, boolean value)
	{
		setProperty(name,value ? "true" : "false");
	} //}}}

	//{{{ setIntegerProperty() method
	/**
	 * Sets the value of an integer property.
	 * @param name The property
	 * @param value The value
	 * @since jEdit 4.0pre1
	 */
	public static final void setIntegerProperty(String name, int value)
	{
		setProperty(name,String.valueOf(value));
	} //}}}

	//{{{ setFontProperty() method
	/**
	 * Sets the value of a font property. The family is stored
	 * in the <code><i>name</i></code> property, the font size is stored
	 * in the <code><i>name</i>size</code> property, and the font style is
	 * stored in <code><i>name</i>style</code>. For example, if
	 * <code><i>name</i></code> is <code>view.gutter.font</code>, the
	 * properties will be named <code>view.gutter.font</code>,
	 * <code>view.gutter.fontsize</code>, and
	 * <code>view.gutter.fontstyle</code>.
	 *
	 * @param name The property
	 * @param value The value
	 * @since jEdit 4.0pre1
	 */
	public static final void setFontProperty(String name, Font value)
	{
		setProperty(name,value.getFamily());
		setIntegerProperty(name + "size",value.getSize());
		setIntegerProperty(name + "style",value.getStyle());
	} //}}}

	//{{{ unsetProperty() method
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
	} //}}}

	//{{{ resetProperty() method
	/**
	 * Resets a property to its default value.
	 * @param name The property
	 *
	 * @since jEdit 2.5pre3
	 */
	public static final void resetProperty(String name)
	{
		props.remove(name);
	} //}}}

	//{{{ propertiesChanged() method
	/**
	 * Reloads various settings from the properties.
	 */
	public static void propertiesChanged()
	{
		initKeyBindings();

		Autosave.setInterval(getIntegerProperty("autosave",30));

		saveCaret = getBooleanProperty("saveCaret");

		//theme = new JEditMetalTheme();
		//theme.propertiesChanged();
		//MetalLookAndFeel.setCurrentTheme(theme);

		UIDefaults defaults = UIManager.getDefaults();

		// give all text areas the same font
		Font font = getFontProperty("view.font");

		//defaults.put("TextField.font",font);
		defaults.put("TextArea.font",font);
		defaults.put("TextPane.font",font);

		initProxy();

		EditBus.send(new PropertiesChanged(null));
	} //}}}

	//}}}

	//{{{ Plugin management methods

	//{{{ getNotLoadedPluginJARs() method
	/**
	 * Returns a list of plugin JARs that are not currently loaded
	 * by examining the user and system plugin directories.
	 * @since jEdit 3.2pre1
	 */
	public static String[] getNotLoadedPluginJARs()
	{
		Vector returnValue = new Vector();

		if(jEditHome != null)
		{
			String systemPluginDir = MiscUtilities
				.constructPath(jEditHome,"jars");

			String[] list = new File(systemPluginDir).list();
			if(list != null)
				getNotLoadedPluginJARs(returnValue,systemPluginDir,list);
		}

		if(settingsDirectory != null)
		{
			String userPluginDir = MiscUtilities
				.constructPath(settingsDirectory,"jars");
			String[] list = new File(userPluginDir).list();
			if(list != null)
			{
				getNotLoadedPluginJARs(returnValue,
					userPluginDir,list);
			}
		}

		String[] _returnValue = new String[returnValue.size()];
		returnValue.copyInto(_returnValue);
		return _returnValue;
	} //}}}

	//{{{ getPlugin() method
	/**
	 * Returns the plugin with the specified class name.
	 */
	public static EditPlugin getPlugin(String name)
	{
		EditPlugin[] plugins = getPlugins();
		for(int i = 0; i < plugins.length; i++)
		{
			if(plugins[i].getClassName().equals(name))
				return plugins[i];
		}

		return null;
	} //}}}

	//{{{ getPlugins() method
	/**
	 * Returns an array of installed plugins.
	 */
	public static EditPlugin[] getPlugins()
	{
		Vector vector = new Vector();
		for(int i = 0; i < jars.size(); i++)
		{
			((EditPlugin.JAR)jars.elementAt(i)).getPlugins(vector);
		}

		EditPlugin[] array = new EditPlugin[vector.size()];
		vector.copyInto(array);
		return array;
	} //}}}

	//{{{ getPluginJARs() method
	/**
	 * Returns an array of installed plugins.
	 * @since jEdit 2.5pre3
	 */
	public static EditPlugin.JAR[] getPluginJARs()
	{
		EditPlugin.JAR[] array = new EditPlugin.JAR[jars.size()];
		jars.copyInto(array);
		return array;
	} //}}}

	//{{{ getPluginJAR() method
	/**
	 * Returns the JAR with the specified path name.
	 * @param path The path name
	 * @since jEdit 2.6pre1
	 */
	public static EditPlugin.JAR getPluginJAR(String path)
	{
		for(int i = 0; i < jars.size(); i++)
		{
			EditPlugin.JAR jar = (EditPlugin.JAR)jars.elementAt(i);
			if(jar.getPath().equals(path))
				return jar;
		}

		return null;
	} //}}}

	//{{{ addPluginJAR() method
	/**
	 * Adds a plugin JAR to the editor.
	 * @param plugin The plugin
	 * @since jEdit 3.2pre10
	 */
	public static void addPluginJAR(EditPlugin.JAR plugin)
	{
		addActionSet(plugin.getActions());
		jars.addElement(plugin);
	} //}}}

	//}}}

	//{{{ Action methods

	//{{{ addActionSet() method
	/**
	 * Adds a new action set to jEdit's list. Plugins probably won't
	 * need to call this method.
	 * @since jEdit 4.0pre1
	 */
	public static void addActionSet(ActionSet actionSet)
	{
		actionSets.addElement(actionSet);
	} //}}}

	//{{{ getActionSets() method
	/**
	 * Returns all registered action sets.
	 * @since jEdit 4.0pre1
	 */
	public static ActionSet[] getActionSets()
	{
		ActionSet[] retVal = new ActionSet[actionSets.size()];
		actionSets.copyInto(retVal);
		return retVal;
	} //}}}

	//{{{ getAction() method
	/**
	 * Returns the specified action.
	 * @param name The action name
	 */
	public static EditAction getAction(String name)
	{
		for(int i = 0; i < actionSets.size(); i++)
		{
			EditAction action = ((ActionSet)actionSets.elementAt(i))
				.getAction(name);
			if(action != null)
				return action;
		}

		return null;
	} //}}}

	//{{{ getActionSetForAction() method
	/**
	 * Returns the action set that contains the specified action.
	 * @param action The action
	 * @since jEdit 4.0pre1
	 */
	public static ActionSet getActionSetForAction(EditAction action)
	{
		for(int i = 0; i < actionSets.size(); i++)
		{
			ActionSet set = (ActionSet)actionSets.elementAt(i);
			if(set.contains(action))
				return set;
		}

		return null;
	} //}}}

	//{{{ getActions() method
	/**
	 * Returns the list of actions registered with the editor.
	 */
	public static EditAction[] getActions()
	{
		Vector vec = new Vector();
		for(int i = 0; i < actionSets.size(); i++)
			((ActionSet)actionSets.elementAt(i)).getActions(vec);

		EditAction[] retVal = new EditAction[vec.size()];
		vec.copyInto(retVal);
		return retVal;
	} //}}}

	//}}}

	//{{{ Edit mode methods

	//{{{ reloadModes() method
	/**
	 * Reloads all edit modes.
	 * @since jEdit 3.2pre2
	 */
	public static void reloadModes()
	{
		/* Try to guess the eventual size to avoid unnecessary
		 * copying */
		modes = new Vector(50);

		//{{{ Load the global catalog
		if(jEditHome == null)
			loadModeCatalog("/modes/catalog",true);
		else
		{
			loadModeCatalog(MiscUtilities.constructPath(jEditHome,
				"modes","catalog"),false);
		} //}}}

		//{{{ Load user catalog
		if(settingsDirectory != null)
		{
			File userModeDir = new File(MiscUtilities.constructPath(
				settingsDirectory,"modes"));
			if(!userModeDir.exists())
				userModeDir.mkdirs();

			File userCatalog = new File(MiscUtilities.constructPath(
				settingsDirectory,"modes","catalog"));
			if(!userCatalog.exists())
			{
				// create dummy catalog
				try
				{
					FileWriter out = new FileWriter(userCatalog);
					out.write(jEdit.getProperty("defaultCatalog"));
					out.close();
				}
				catch(IOException io)
				{
					Log.log(Log.ERROR,jEdit.class,io);
				}
			}

			loadModeCatalog(userCatalog.getPath(),false);
		} //}}}

		Buffer buffer = buffersFirst;
		while(buffer != null)
		{
			// This reloads the token marker and sends a message
			// which causes edit panes to repaint their text areas
			buffer.setMode();

			buffer = buffer.next;
		}
	} //}}}

	//{{{ getMode() method
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
	} //}}}

	//{{{ getModes() method
	/**
	 * Returns an array of installed edit modes.
	 */
	public static Mode[] getModes()
	{
		Mode[] array = new Mode[modes.size()];
		modes.copyInto(array);
		return array;
	} //}}}

	//}}}

	//{{{ Buffer creation methods

	//{{{ showOpenFileDialog() method
	/**
	 * Displays the open file dialog box, and opens any selected files.
	 *
	 * @param view The view
	 * @since jEdit 2.7pre2
	 */
	public static void showOpenFileDialog(View view)
	{
		showOpenFileDialog(view,null);
	} //}}}

	//{{{ showOpenFileWithOtherEncodingDialog() method
	/**
	 * Displays the open file dialog box, and opens any selected files,
	 * but first prompts for a character encoding to use.
	 *
	 * @param view The view
	 * @since jEdit 2.7pre2
	 */
	public static void showOpenFileWithOtherEncodingDialog(View view)
	{
		String encoding = GUIUtilities.input(view,"encoding-prompt",null,
			jEdit.getProperty("buffer.encoding",
			System.getProperty("file.encoding")));
		if(encoding == null)
			return;

		Macros.Recorder recorder = view.getMacroRecorder();
		if(recorder != null)
		{
			recorder.record("props = new Hashtable();");
			recorder.record("props.put(\"encoding\",\"" + encoding + "\");");
			recorder.record("jEdit.showOpenFileDialog(view,props);");
		}

		Hashtable props = new Hashtable();
		props.put(Buffer.ENCODING,encoding);
		showOpenFileDialog(view,props);
	} //}}}

	//{{{ showOpenFileDialog() method
	/**
	 * Displays the open file dialog box, and opens any selected files,
	 * setting the properties specified in the hash table in the buffers.
	 *
	 * @param view The view
	 * @param props The properties to set in the buffer
	 * @since jEdit 3.2pre2
	 */
	public static void showOpenFileDialog(View view, Hashtable props)
	{
		String[] files = GUIUtilities.showVFSFileDialog(view,null,
			VFSBrowser.OPEN_DIALOG,true);

		Buffer buffer = null;
		if(files != null)
		{
			for(int i = 0; i < files.length; i++)
			{
				Buffer newBuffer = openFile(null,null,files[i],
					false,props);
				if(newBuffer != null)
					buffer = newBuffer;
			}
		}

		if(buffer != null)
			view.setBuffer(buffer);
	} //}}}

	//{{{ restoreOpenFiles() method
	/**
	 * Opens files that were open last time.
	 * @since jEdit 3.2pre2
	 */
	public static String restoreOpenFiles()
	{
		if(settingsDirectory == null)
			return null;

		File session = new File(MiscUtilities.constructPath(
			settingsDirectory,"session"));

		if(!session.exists())
			return null;

		String splitConfig = null;

		try
		{
			BufferedReader in = new BufferedReader(new FileReader(
				session));

			String line;
			while((line = in.readLine()) != null)
			{
				if(line.startsWith("splits\t"))
					splitConfig = line.substring(7);
				else
					openFile(null,line);
			}

			in.close();
		}
		catch(IOException io)
		{
			Log.log(Log.ERROR,jEdit.class,"Error while loading " + session);
			Log.log(Log.ERROR,jEdit.class,io);
		}

		return splitConfig;
	} //}}}

	//{{{ saveOpenFiles() method
	/**
	 * Saves the list of open files.
	 * @since jEdit 3.1pre5
	 */
	public static void saveOpenFiles(View view)
	{
		if(settingsDirectory == null)
			return;

		view.getEditPane().saveCaretInfo();
		Buffer current = view.getBuffer();

		File session = new File(MiscUtilities.constructPath(
			settingsDirectory,"session"));

		// maybe not, since it's autosaved now
		//backupSettingsFile(session);

		try
		{
			String lineSep = System.getProperty("line.separator");

			BufferedWriter out = new BufferedWriter(new FileWriter(
				session));
			Buffer buffer = buffersFirst;
			while(buffer != null)
			{
				if(!buffer.isUntitled())
				{
					out.write(buffer.getPath());
					out.write(lineSep);
				}

				buffer = buffer.next;
			}

			out.write("splits\t");
			out.write(view.getSplitConfig());
			out.write(lineSep);

			out.close();
		}
		catch(IOException io)
		{
			Log.log(Log.ERROR,jEdit.class,"Error while saving " + session);
			Log.log(Log.ERROR,jEdit.class,io);
		}
	} //}}}

	//{{{ openFiles() method
	/**
	 * Opens the file names specified in the argument array. This
	 * handles +line and +marker arguments just like the command
	 * line parser.
	 * @param parent The parent directory
	 * @param args The file names to open
	 * @since jEdit 3.2pre4
	 */
	public static Buffer openFiles(View view, String parent, String[] args)
	{
		Buffer retVal = null;
		Buffer lastBuffer = null;

		for(int i = 0; i < args.length; i++)
		{
			String arg = args[i];
			if(arg == null)
				continue;
			else if(arg.startsWith("+line:") || arg.startsWith("+marker:"))
			{
				if(lastBuffer != null)
					gotoMarker(view,lastBuffer,arg);
				continue;
			}

			lastBuffer = openFile(null,parent,arg,false,null);

			if(retVal == null && lastBuffer != null)
				retVal = lastBuffer;
		}

		if(view != null && retVal != null)
			view.setBuffer(retVal);

		return retVal;
	} //}}}

	//{{{ openFile() method
	/**
	 * Opens a file. Note that as of jEdit 2.5pre1, this may return
	 * null if the buffer could not be opened.
	 * @param view The view to open the file in
	 * @param path The file path
	 *
	 * @since jEdit 2.4pre1
	 */
	public static Buffer openFile(View view, String path)
	{
		return openFile(view,null,path,false,new Hashtable());
	} //}}}

	//{{{ openFile() method
	/**
	 * @deprecated The openFile() forms with the readOnly parameter
	 * should not be used. The readOnly prameter is no longer supported.
	 */
	public static Buffer openFile(View view, String parent,
		String path, boolean readOnly, boolean newFile)
	{
		return openFile(view,parent,path,newFile,new Hashtable());
	} //}}}

	//{{{ openFile() method
	/**
	 * @deprecated The openFile() forms with the readOnly parameter
	 * should not be used. The readOnly prameter is no longer supported.
	 */
	public static Buffer openFile(View view, String parent,
		String path, boolean readOnly, boolean newFile,
		Hashtable props)
	{
		return openFile(view,parent,path,newFile,props);
	} //}}}

	//{{{ openFile() method
	/**
	 * Opens a file. This may return null if the buffer could not be
	 * opened for some reason.
	 * @param view The view to open the file in
	 * @param parent The parent directory of the file
	 * @param path The path name of the file
	 * @param newFile True if the file should not be loaded from disk
	 * be prompted if it should be reloaded
	 * @param props Buffer-local properties to set in the buffer
	 *
	 * @since jEdit 3.2pre10
	 */
	public static Buffer openFile(View view, String parent,
		String path, boolean newFile, Hashtable props)
	{
		if(view != null && parent == null)
			parent = MiscUtilities.getParentOfPath(view.getBuffer().getPath());

		if(MiscUtilities.isURL(path))
		{
			if(MiscUtilities.getProtocolOfURL(path).equals("file"))
				path = path.substring(5);
		}

		path = MiscUtilities.constructPath(parent,path);

		if(!MiscUtilities.isURL(path))
			path = MiscUtilities.canonPath(path);

		synchronized(bufferListLock)
		{
			Buffer buffer = getBuffer(path);
			if(buffer != null)
			{
				if(view != null)
					view.setBuffer(buffer);

				return buffer;
			}

			if(props == null)
				props = new Hashtable();

			BufferHistory.Entry entry = BufferHistory.getEntry(path);

			if(entry != null && saveCaret && props.get(Buffer.CARET) == null)
			{
				int caret = entry.caret;
				props.put(Buffer.CARET,new Integer(entry.caret));
				if(entry.selection != null)
				{
					// getSelection() converts from string to
					// Selection[]
					props.put(Buffer.SELECTION,entry.getSelection());
				}
			}

			if(entry != null && props.get(Buffer.ENCODING) == null)
			{
				if(entry.encoding != null)
					props.put(Buffer.ENCODING,entry.encoding);
			}

			Buffer newBuffer = new Buffer(path,newFile,false,props);

			if(!newBuffer.load(view,false))
				return null;

			addBufferToList(newBuffer);

			EditBus.send(new BufferUpdate(newBuffer,view,BufferUpdate.CREATED));

			if(view != null)
				view.setBuffer(newBuffer);

			return newBuffer;
		}
	} //}}}

	//{{{ openTemporary() method
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
	 *
	 * @since jEdit 3.2pre10
	 */
	public static Buffer openTemporary(View view, String parent,
		String path, boolean newFile)
	{
		if(view != null && parent == null)
			parent = MiscUtilities.getParentOfPath(view.getBuffer().getPath());

		if(MiscUtilities.isURL(path))
		{
			if(MiscUtilities.getProtocolOfURL(path).equals("file"))
				path = path.substring(5);
		}

		path = MiscUtilities.constructPath(parent,path);

		synchronized(bufferListLock)
		{
			Buffer buffer = getBuffer(path);
			if(buffer != null)
				return buffer;

			buffer = new Buffer(path,newFile,true,new Hashtable());
			if(!buffer.load(view,false))
				return null;
			else
				return buffer;
		}
	} //}}}

	//{{{ commitTemporary() method
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

		addBufferToList(buffer);
		buffer.commitTemporary();

		EditBus.send(new BufferUpdate(buffer,null,BufferUpdate.CREATED));
	} //}}}

	//{{{ newFile() method
	/**
	 * Creates a new `untitled' file.
	 * @param view The view to create the file in
	 */
	public static Buffer newFile(View view)
	{
		String path;

		if(view != null && view.getBuffer() != null)
		{
			path = MiscUtilities.getParentOfPath(view.getBuffer()
				.getPath());
			VFS vfs = VFSManager.getVFSForPath(path);
			// don't want 'New File' to create a read only buffer
			// if current file is on SQL VFS or something
			if((vfs.getCapabilities() & VFS.WRITE_CAP) == 0)
				path = System.getProperty("user.home");
		}
		else
			path = null;

		return newFile(view,path);
	} //}}}

	//{{{ newFile() method
	/**
	 * Creates a new `untitled' file.
	 * @param view The view to create the file in
	 * @param dir The directory to create the file in
	 * @since jEdit 3.1pre2
	 */
	public static Buffer newFile(View view, String dir)
	{
		// If only one new file is open which is clean, just close
		// it, which will create an 'Untitled-1'
		if(dir != null
			&& buffersFirst != null
			&& buffersFirst == buffersLast
			&& buffersFirst.isUntitled()
			&& !buffersFirst.isDirty())
		{
			closeBuffer(view,buffersFirst);
			// return the newly created 'untitled-1'
			return buffersFirst;
		}

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

		return openFile(view,dir,"Untitled-" + (untitledCount+1),true,null);
	} //}}}

	//}}}

	//{{{ Buffer management methods

	//{{{ closeBuffer() method
	/**
	 * Closes a buffer. If there are unsaved changes, the user is
	 * prompted if they should be saved first.
	 * @param view The view
	 * @param buffer The buffer
	 * @return True if the buffer was really closed, false otherwise
	 */
	public static boolean closeBuffer(View view, Buffer buffer)
	{
		// Wait for pending I/O requests
		if(buffer.isPerformingIO())
		{
			VFSManager.waitForRequests();
			if(VFSManager.errorOccurred())
				return false;
		}

		if(buffer.isDirty())
		{
			Object[] args = { buffer.getName() };
			int result = GUIUtilities.confirm(view,"notsaved",args,
				JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE);
			if(result == JOptionPane.YES_OPTION)
			{
				if(!buffer.save(view,null,true))
					return false;
			}
			else if(result != JOptionPane.NO_OPTION)
				return false;
		}

		_closeBuffer(view,buffer);

		return true;
	} //}}}

	//{{{ _closeBuffer() method
	/**
	 * Closes the buffer, even if it has unsaved changes.
	 * @param view The view
	 * @param buffer The buffer
	 *
	 * @since jEdit 2.2pre1
	 */
	public static void _closeBuffer(View view, Buffer buffer)
	{
		if(buffer.isClosed())
		{
			// can happen if the user presses C+w twice real
			// quick and the buffer has unsaved changes
			return;
		}

		if(!buffer.isNewFile())
		{
			view.getEditPane().saveCaretInfo();
			Integer _caret = (Integer)buffer.getProperty(Buffer.CARET);
			int caret = (_caret == null ? 0 : _caret.intValue());

			BufferHistory.setEntry(buffer.getPath(),caret,
				(Selection[])buffer.getProperty(Buffer.SELECTION),
				buffer.getStringProperty(Buffer.ENCODING));
		}

		removeBufferFromList(buffer);
		buffer.close();

		EditBus.send(new BufferUpdate(buffer,view,BufferUpdate.CLOSED));

		// Create a new file when the last is closed
		if(buffersFirst == null && buffersLast == null)
			newFile(view);
	} //}}}

	//{{{ closeAllBuffers() method
	/**
	 * Closes all open buffers.
	 * @param view The view
	 */
	public static boolean closeAllBuffers(View view)
	{
		return closeAllBuffers(view,false);
	} //}}}

	//{{{ closeAllBuffers() method
	/**
	 * Closes all open buffers.
	 * @param view The view
	 * @param isExiting This must be false unless this method is
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

		// Wait for pending I/O requests
		VFSManager.waitForRequests();
		if(VFSManager.errorOccurred())
			return false;

		// close remaining buffers (the close dialog only deals with
		// dirty ones)

		buffer = buffersFirst;

		// zero it here so that BufferTabs doesn't have any problems
		buffersFirst = buffersLast = null;
		bufferCount = 0;

		while(buffer != null)
		{
			if(!buffer.isNewFile())
			{
				Integer _caret = (Integer)buffer.getProperty(Buffer.CARET);
				int caret = (_caret == null ? 0 : _caret.intValue());
				BufferHistory.setEntry(buffer.getPath(),caret,
					(Selection[])buffer.getProperty(Buffer.SELECTION),
					buffer.getStringProperty(Buffer.ENCODING));
			}

			buffer.close();
			if(!isExiting)
			{
				EditBus.send(new BufferUpdate(buffer,view,
					BufferUpdate.CLOSED));
			}
			buffer = buffer.next;
		}

		if(!isExiting)
			newFile(view);

		return true;
	} //}}}

	//{{{ saveAllBuffers() method
	/**
	 * Saves all open buffers.
	 * @param view The view
	 * @param confirm If true, a confirmation dialog will be shown first
	 * @since jEdit 2.7pre2
	 */
	public static void saveAllBuffers(View view, boolean confirm)
	{
		if(confirm)
		{
			int result = GUIUtilities.confirm(view,"saveall",null,
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE);
			if(result != JOptionPane.YES_OPTION)
				return;
		}

		Buffer current = view.getBuffer();

		Buffer buffer = buffersFirst;
		while(buffer != null)
		{
			if(buffer.isDirty())
			{
				if(buffer.isNewFile())
					view.setBuffer(buffer);
				buffer.save(view,null,true);
			}

			buffer = buffer.next;
		}

		view.setBuffer(current);
	} //}}}

	//{{{ reloadAllBuffers() method
	/**
	 * Reloads all open buffers.
	 * @param view The view
	 * @param confirm If true, a confirmation dialog will be shown first
	 * @since jEdit 2.7pre2
	 */
	public static void reloadAllBuffers(final View view, boolean confirm)
	{
		if(confirm)
		{
			int result = GUIUtilities.confirm(view,"reload-all",null,
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE);
			if(result != JOptionPane.YES_OPTION)
				return;
		}

		// save caret info. Buffer.load() will load it.
		View _view = viewsFirst;
		while(_view != null)
		{
			EditPane[] panes = _view.getEditPanes();
			for(int i = 0; i < panes.length; i++)
			{
				panes[i].saveCaretInfo();
			}

			_view = _view.next;
		}

		Buffer[] buffers = jEdit.getBuffers();
		for(int i = 0; i < buffers.length; i++)
		{
			Buffer buffer = buffers[i];
			buffer.load(view,true);
		}
	} //}}}

	//{{{ getBuffer() method
	/**
	 * Returns the buffer with the specified path name. The path name
	 * must be an absolute, canonical, path.
	 * @param path The path name
	 * @see MiscUtilities#constructPath(String,String)
	 */
	public static Buffer getBuffer(String path)
	{
		boolean caseInsensitiveFilesystem = (File.separatorChar == '\\'
			|| File.separatorChar == ':' /* Windows or MacOS */);

		synchronized(bufferListLock)
		{
			Buffer buffer = buffersFirst;
			while(buffer != null)
			{
				String _path = buffer.getPath();
				if(caseInsensitiveFilesystem)
				{
					if(_path.equalsIgnoreCase(path))
						return buffer;
				}
				else
				{
					if(_path.equals(path))
						return buffer;
				}
				buffer = buffer.next;
			}
		}

		return null;
	} //}}}

	//{{{ getBuffers() method
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
	} //}}}

	//{{{ getBufferCount() method
	/**
	 * Returns the number of open buffers.
	 */
	public static int getBufferCount()
	{
		return bufferCount;
	} //}}}

	//{{{ getFirstBuffer() method
	/**
	 * Returns the first buffer.
	 */
	public static Buffer getFirstBuffer()
	{
		return buffersFirst;
	} //}}}

	//{{{ getLastBuffer() method
	/**
	 * Returns the last buffer.
	 */
	public static Buffer getLastBuffer()
	{
		return buffersLast;
	} //}}}

	//}}}

	//{{{ View methods

	//{{{ getInputHandler() method
	/**
	 * Returns the current input handler (key binding to action mapping)
	 * @see org.gjt.sp.jedit.gui.InputHandler
	 */
	public static InputHandler getInputHandler()
	{
		return inputHandler;
	} //}}}

	//{{{ newView() method
	/**
	 * Creates a new view of a buffer.
	 * @param view An existing view
	 * @param buffer The buffer
	 */
	public static View newView(View view, Buffer buffer)
	{
		if(view != null)
		{
			view.showWaitCursor();
			view.getEditPane().saveCaretInfo();
		}

		View newView = new View(buffer,null);

		// Do this crap here so that the view is created
		// and added to the list before it is shown
		// (for the sake of plugins that add stuff to views)
		newView.pack();

		// newView.setSize(view.getSize()) creates incorrectly
		// sized views, for some reason...
		if(view != null)
		{
			GUIUtilities.saveGeometry(view,"view");
			view.hideWaitCursor();
		}

		GUIUtilities.loadGeometry(newView,"view");

		addViewToList(newView);
		EditBus.send(new ViewUpdate(newView,ViewUpdate.CREATED));

		newView.show();

		// show tip of the day
		if(newView == viewsFirst)
		{
			// Don't show the welcome message if jEdit was started
			// with the -nosettings switch
			if(settingsDirectory != null && getBooleanProperty("firstTime"))
				new HelpViewer();
			else if(jEdit.getBooleanProperty("tip.show"))
				new TipOfTheDay(newView);

			setBooleanProperty("firstTime",false);
		}
		else
			GUIUtilities.requestFocus(newView,newView.getTextArea());

		return newView;
	} //}}}

	//{{{ newView() method
	/**
	 * Creates a new view.
	 * @param view An existing view
	 * @since jEdit 3.2pre2
	 */
	public static View newView(View view)
	{
		return newView(view,view.getSplitConfig());
	} //}}}

	//{{{ newView() method
	/**
	 * Creates a new view.
	 * @param view An existing view
	 * @param splitConfig The split configuration
	 * @since jEdit 3.2pre2
	 */
	public static View newView(View view, String splitConfig)
	{
		if(view != null)
		{
			view.showWaitCursor();
			view.getEditPane().saveCaretInfo();
		}

		View newView = new View(null,splitConfig);

		// Do this crap here so that the view is created
		// and added to the list before it is shown
		// (for the sake of plugins that add stuff to views)
		newView.pack();

		// newView.setSize(view.getSize()) creates incorrectly
		// sized views, for some reason...
		if(view != null)
		{
			GUIUtilities.saveGeometry(view,"view");
			view.hideWaitCursor();
		}

		GUIUtilities.loadGeometry(newView,"view");

		addViewToList(newView);
		EditBus.send(new ViewUpdate(newView,ViewUpdate.CREATED));

		newView.show();

		// show tip of the day
		if(newView == viewsFirst)
		{
			// Don't show the welcome message if jEdit was started
			// with the -nosettings switch
			if(settingsDirectory != null && getBooleanProperty("firstTime"))
				new HelpViewer();
			else if(jEdit.getBooleanProperty("tip.show"))
				new TipOfTheDay(newView);

			setBooleanProperty("firstTime",false);
		}
		else
			GUIUtilities.requestFocus(newView,newView.getTextArea());


		return newView;
	} //}}}

	//{{{ closeView() method
	/**
	 * Closes a view. jEdit will exit if this was the last open view.
	 */
	public static void closeView(View view)
	{
		closeView(view,true);
	} //}}}

	//{{{ getViews() method
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
	} //}}}

	//{{{ getViewCount() method
	/**
	 * Returns the number of open views.
	 */
	public static int getViewCount()
	{
		return viewCount;
	} //}}}

	//{{{ getFirstView() method
	/**
	 * Returns the first view.
	 */
	public static View getFirstView()
	{
		return viewsFirst;
	} //}}}

	//{{{ getLastView() method
	/**
	 * Returns the last view.
	 */
	public static View getLastView()
	{
		return viewsLast;
	} //}}}

	//}}}

	//{{{ Miscellaneous methods

	//{{{ isBackgroundMode() method
	/**
	 * Returns true if jEdit was started with the <code>-background</code>
	 * command-line switch.
	 * @since jEdit 4.0pre4
	 */
	public static boolean isBackgroundModeEnabled()
	{
		return background;
	} //}}}

	//{{{ showMemoryStatusDialog() method
	/**
	 * Performs garbage collection and displays a dialog box showing
	 * memory status.
	 * @param view The view
	 * @since jEdit 4.0pre1
	 */
	public static void showMemoryDialog(View view)
	{
		Runtime rt = Runtime.getRuntime();
		int before = (int) (rt.freeMemory() / 1024);
		System.gc();
		int after = (int) (rt.freeMemory() / 1024);
		int total = (int) (rt.totalMemory() / 1024);

		JProgressBar progress = new JProgressBar(0,total);
		progress.setValue(total - after);
		progress.setStringPainted(true);
		progress.setString(jEdit.getProperty("memory-status.use",
			new Object[] { new Integer(total - after),
			new Integer(total) }));

		Object[] message = new Object[4];
		message[0] = getProperty("memory-status.gc",
			new Object[] { new Integer(after - before) });
		message[1] = Box.createVerticalStrut(12);
		message[2] = progress;
		message[3] = Box.createVerticalStrut(6);

		JOptionPane.showMessageDialog(view,message,
			jEdit.getProperty("memory-status.title"),
			JOptionPane.INFORMATION_MESSAGE);
	} //}}}

	//{{{ getJEditHome() method
	/**
	 * Returns the jEdit install directory.
	 */
	public static String getJEditHome()
	{
		return jEditHome;
	} //}}}

	//{{{ getSettingsDirectory() method
	/**
	 * Returns the user settings directory.
	 */
	public static String getSettingsDirectory()
	{
		return settingsDirectory;
	} //}}}

	//{{{ backupSettingsFile() method
	/**
	 * Backs up the specified file in the settings directory.
	 * You should call this on any settings files your plugin
	 * writes.
	 * @param file The file
	 * @since jEdit 4.0pre1
	 */
	public static void backupSettingsFile(File file)
	{
		if(settingsDirectory == null)
			return;

		String backupDir = MiscUtilities.constructPath(
			settingsDirectory,"settings-backup");
		File dir = new File(backupDir);
		if(!dir.exists())
			dir.mkdirs();

		// ... sweet. saveBackup() will create backupDir if it
		// doesn't exist.

		MiscUtilities.saveBackup(file,5,null,"~",backupDir);
	} //}}}

	//{{{ saveSettings() method
	/**
	 * Saves all user preferences to disk.
	 */
	public static void saveSettings()
	{
		if(settingsDirectory != null)
		{
			// Save the recent file list
			File file1 = new File(MiscUtilities.constructPath(
				settingsDirectory, "#recent.xml#save#"));
			File file2 = new File(MiscUtilities.constructPath(
				settingsDirectory, "recent.xml"));
			if(file2.exists() && file2.lastModified() != recentModTime)
			{
				Log.log(Log.WARNING,jEdit.class,file2 + " changed"
					+ " on disk; will not save recent files");
			}
			else
			{
				backupSettingsFile(file2);
				BufferHistory.save(file1);
				file2.delete();
				file1.renameTo(file2);
			}
			recentModTime = file2.lastModified();

			file1 = new File(MiscUtilities.constructPath(
				settingsDirectory, "#history#save#"));
			file2 = new File(MiscUtilities.constructPath(
				settingsDirectory, "history"));
			if(file2.exists() && file2.lastModified() != historyModTime)
			{
				Log.log(Log.WARNING,jEdit.class,file2 + " changed"
					+ " on disk; will not save history");
			}
			else
			{
				backupSettingsFile(file2);
				HistoryModel.saveHistory(file1);
				file2.delete();
				file1.renameTo(file2);
			}
			historyModTime = file2.lastModified();

			SearchAndReplace.save();
			Abbrevs.save();
			FavoritesVFS.saveFavorites();

			file1 = new File(MiscUtilities.constructPath(
				settingsDirectory,"#properties#save#"));
			file2 = new File(MiscUtilities.constructPath(
				settingsDirectory,"properties"));
			if(file2.exists() && file2.lastModified() != propsModTime)
			{
				Log.log(Log.WARNING,jEdit.class,file2 + " changed"
					+ " on disk; will not save user properties");
			}
			else
			{
				backupSettingsFile(file2);

				try
				{
					OutputStream out = new FileOutputStream(file1);
					props.store(out,"jEdit properties");
					out.close();
				}
				catch(IOException io)
				{
					Log.log(Log.ERROR,jEdit.class,io);
				}

				file2.delete();
				file1.renameTo(file2);
			}
			propsModTime = file2.lastModified();
		}
	} //}}}

	//{{{ exit() method
	/**
	 * Exits cleanly from jEdit, prompting the user if any unsaved files
	 * should be saved first.
	 * @param view The view from which this exit was called
	 * @param reallyExit If background mode is enabled and this parameter
	 * is true, then jEdit will close all open views instead of exiting
	 * entirely.
	 */
	public static void exit(View view, boolean reallyExit)
	{
		// Wait for pending I/O requests
		VFSManager.waitForRequests();

		// Send EditorExitRequested
		EditBus.send(new EditorExitRequested(view));

		// Even if reallyExit is false, we still exit properly
		// if background mode is off
		reallyExit |= !background;

		saveOpenFiles(view);

		// Close all buffers
		if(!closeAllBuffers(view,reallyExit))
			return;

		// If we are running in background mode and
		// reallyExit was not specified, then return here.
		if(!reallyExit)
		{
			// in this case, we can't directly call
			// view.close(); we have to call closeView()
			// for all open views
			view = viewsFirst;
			while(view != null)
			{
				closeView(view,false);
				view = view.next;
			}

			// Save settings in case user kills the backgrounded
			// jEdit process
			saveSettings();

			return;
		}

		// Save view properties here - it unregisters
		// listeners, and we would have problems if the user
		// closed a view but cancelled an unsaved buffer close
		view.close();

		// Stop autosave timer
		Autosave.stop();

		// Stop server
		if(server != null)
			server.stopServer();

		// Stop all plugins
		EditPlugin[] plugins = getPlugins();
		for(int i = 0; i < plugins.length; i++)
		{
			try
			{
				plugins[i].stop();
			}
			catch(Throwable t)
			{
				Log.log(Log.ERROR,jEdit.class,"Error while "
					+ "stopping plugin:");
				Log.log(Log.ERROR,jEdit.class,t);
			}
		}

		// Send EditorExiting
		EditBus.send(new EditorExiting(null));

		// Save settings
		saveSettings();

		// Close activity log stream
		Log.closeStream();

		// Byebye...
		System.exit(0);
	} //}}}

	//}}}

	//{{{ Package-private members

	//{{{ updatePosition() method
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
	} //}}}

	//{{{ addMode() method
	/**
	 * Do not call this method. It is only public so that classes
	 * in the org.gjt.sp.jedit.syntax package can access it.
	 * @param mode The edit mode
	 */
	public static void addMode(Mode mode)
	{
		Log.log(Log.DEBUG,jEdit.class,"Adding edit mode "
			+ mode.getName());

		modes.addElement(mode);
	} //}}}

	//{{{ loadMode() method
	/**
	 * Loads an XML-defined edit mode from the specified reader.
	 * @param mode The edit mode
	 */
	/* package-private */ static void loadMode(Mode mode)
	{
		Object fileName = mode.getProperty("file");

		Log.log(Log.NOTICE,jEdit.class,"Loading edit mode " + fileName);

		XmlParser parser = new XmlParser();
		XModeHandler xmh = new XModeHandler(parser,mode.getName(),fileName.toString());
		parser.setHandler(xmh);
		try
		{
			Reader grammar;
			if(fileName instanceof URL)
			{
				grammar = new BufferedReader(
					new InputStreamReader(
					((URL)fileName).openStream()));
			}
			else
			{
				grammar = new BufferedReader(new FileReader(
					(String)fileName));
			}

			parser.parse(null, null, grammar);
		}
		catch (Throwable e)
		{
			Log.log(Log.ERROR, jEdit.class, e);

			if (e instanceof XmlException)
			{
				XmlException xe = (XmlException) e;
				int line = xe.getLine();
				String message = xe.getMessage();

				Object[] args = { fileName, new Integer(line), message };
				GUIUtilities.error(null,"xmode-parse",args);
			}

			// give it an empty token marker to avoid problems
			TokenMarker marker = new TokenMarker();
			marker.addRuleSet("MAIN",new ParserRuleSet("MAIN",mode));
			mode.setTokenMarker(marker);
		}
	} //}}}

	//{{{ loadProps() method
	/**
	 * Loads the properties from the specified input stream. This
	 * calls the <code>load()</code> method of the properties object
	 * and closes the stream.
	 * @param in The input stream
	 * @param def If true, the properties will be loaded into the
	 * default table
	 * @exception IOException if an I/O error occured
	 */
	/* package-private */ static void loadProps(InputStream in, boolean def)
		throws IOException
	{
		in = new BufferedInputStream(in);
		if(def)
			defaultProps.load(in);
		else
			props.load(in);
		in.close();
	} //}}}

	//{{{ loadActions() method
	/**
	 * Loads the specified action list.
	 */
	static boolean loadActions(String path, Reader in, ActionSet actionSet)
	{
		try
		{
			Log.log(Log.DEBUG,jEdit.class,"Loading actions from " + path);

			ActionListHandler ah = new ActionListHandler(path,actionSet);
			XmlParser parser = new XmlParser();
			parser.setHandler(ah);
			parser.parse(null, null, in);
			return true;
		}
		catch(XmlException xe)
		{
			int line = xe.getLine();
			String message = xe.getMessage();
			Log.log(Log.ERROR,jEdit.class,path + ":" + line
				+ ": " + message);
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,jEdit.class,e);
		}

		return false;
	} //}}}

	//{{{ pluginError() method
	static void pluginError(final String path, String messageProp, Object[] args)
	{
		if(pluginErrors == null)
			pluginErrors = new Vector();

		pluginErrors.addElement(new ErrorListDialog.ErrorEntry(
			path,messageProp,args));
	} //}}}

	//}}}

	//{{{ Private members

	//{{{ Static variables
	private static String jEditHome;
	private static String settingsDirectory;
	private static long propsModTime, historyModTime, recentModTime;
	private static Properties defaultProps;
	private static Properties props;
	private static EditServer server;
	private static boolean background;
	private static Vector actionSets;
	private static ActionSet builtInActionSet;
	private static Vector pluginErrors;
	private static Vector jars;
	private static Vector modes;
	private static Vector recent;
	private static boolean saveCaret;
	private static InputHandler inputHandler;
	private static JEditMetalTheme theme;

	// buffer link list
	private static boolean sortBuffers;
	private static boolean sortByName;
	private static int bufferCount;
	private static Buffer buffersFirst;
	private static Buffer buffersLast;

	// makes openTemporary() thread-safe
	private static Object bufferListLock = new Object();

	// view link list
	private static int viewCount;
	private static View viewsFirst;
	private static View viewsLast;
	//}}}

	private jEdit() {}

	//{{{ usage() method
	private static void usage()
	{
		System.out.println("Usage: jedit [<options>] [<files>]");

		System.out.println("	<file> +marker:<marker>: Positions caret"
			+ " at marker <marker>");
		System.out.println("	<file> +line:<line>: Positions caret"
			+ " at line number <line>");
		System.out.println("	--: End of options");
		System.out.println("	-background: Run in background mode");
		System.out.println("	-nogui: Only if running in background mode;"
			+ " don't open initial view");
		System.out.println("	-norestore: Don't restore previously open files");
		System.out.println("	-run=<script>: Run the specified BeanShell script");
		System.out.println("	-server: Read/write server"
			+ " info from/to $HOME/.jedit/server");
		System.out.println("	-server=<name>: Read/write server"
			+ " info from/to $HOME/.jedit/<name>");
		System.out.println("	-noserver: Don't start edit server");
		System.out.println("	-settings=<path>: Load user-specific"
			+ " settings from <path>");
		System.out.println("	-nosettings: Don't load user-specific"
			+ " settings");
		System.out.println("	-noplugins: Don't load any plugins");
		System.out.println("	-nostartupscripts: Don't run startup scripts");
		System.out.println("	-version: Print jEdit version and exit");
		System.out.println("	-usage: Print this message and exit");
		System.out.println();
		System.out.println("To set minimum activity log level,"
			+ " specify a number as the first");
		System.out.println("command line parameter"
			+ " (1-9, 1 = print everything, 9 = fatal errors only)");
		System.out.println();
		System.out.println("Report bugs to Slava Pestov <slava@jedit.org>.");
	} //}}}

	//{{{ version() method
	private static void version()
	{
		System.out.println("jEdit " + getVersion());
	} //}}}

	//{{{ makeServerScript() method
	/**
	 * Creates a BeanShell script that can be sent to a running edit server.
	 */
	private static String makeServerScript(boolean restore,
		String[] args, String scriptFile)
	{
		StringBuffer script = new StringBuffer();

		String userDir = System.getProperty("user.dir");

		script.append("parent = \"");
		script.append(MiscUtilities.charsToEscapes(userDir));
		script.append("\";\n");

		script.append("args = new String[");
		script.append(args.length);
		script.append("];\n");

		for(int i = 0; i < args.length; i++)
		{
			script.append("args[");
			script.append(i);
			script.append("] = ");

			if(args[i] == null)
				script.append("null");
			else
			{
				script.append('"');
				script.append(MiscUtilities.charsToEscapes(args[i]));
				script.append('"');
			}

			script.append(";\n");
		}

		script.append("EditServer.handleClient(" + restore + ",parent,args);\n");

		if(scriptFile != null)
		{
			scriptFile = MiscUtilities.constructPath(userDir,scriptFile);
			script.append("BeanShell.runScript(null,\""
				+ MiscUtilities.charsToEscapes(scriptFile)
				+ "\",null,false);\n");
		}

		return script.toString();
	} //}}}

	//{{{ initMisc() method
	/**
	 * Initialise various objects, register protocol handlers.
	 */
	private static void initMisc()
	{
		// Add our protocols to java.net.URL's list
		System.getProperties().put("java.protocol.handler.pkgs",
			"org.gjt.sp.jedit.proto|" +
			System.getProperty("java.protocol.handler.pkgs",""));

		// Set the User-Agent string used by the java.net HTTP handler
		String userAgent = "jEdit/" + getVersion()
			+ " (Java " + System.getProperty("java.version")
			+ ". " + System.getProperty("java.vendor")
			+ "; " + System.getProperty("os.arch") + ")";
		System.getProperties().put("http.agent",userAgent);

		inputHandler = new DefaultInputHandler(null);

		/* Determine installation directory.
		 * If the jedit.home property is set, use that.
		 * Then, look for jedit.jar in the classpath.
		 * If that fails, assume this is the web start version. */
		jEditHome = System.getProperty("jedit.home");
		if(jEditHome == null)
		{
			String classpath = System
				.getProperty("java.class.path");
			int index = classpath.toLowerCase()
				.indexOf("jedit.jar");
			int start = classpath.lastIndexOf(File
				.pathSeparator,index) + 1;
			// if started with java -jar jedit.jar
			 if(classpath.equalsIgnoreCase("jedit.jar"))
			{
				jEditHome = System.getProperty("user.dir");
			}
			else if(index > start)
			{
				jEditHome = classpath.substring(start,
					index - 1);
			}
			else
			{
				// check if web start
				/* if(jEdit.class.getResource("/modes/catalog") != null)
				{
					// modes bundled in; hence web start
					jEditHome = null;
				}
				else */
				{
					// use user.dir as last resort
					jEditHome = System.getProperty("user.dir");

					Log.log(Log.WARNING,jEdit.class,"jedit.jar not in class path!");
					Log.log(Log.WARNING,jEdit.class,"Assuming jEdit is installed in "
						+ jEditHome + ".");
					Log.log(Log.WARNING,jEdit.class,"Override with jedit.home "
						+ "system property.");
				}
			}
		}

		Log.log(Log.MESSAGE,jEdit.class,"jEdit home directory is " + jEditHome);

		//if(jEditHome == null)
		//	Log.log(Log.DEBUG,jEdit.class,"Web start mode");

		jars = new Vector();

		// Add an EditBus component that will reload edit modes and
		// macros if they are changed from within the editor
		EditBus.addToBus(new SettingsReloader());

		// Perhaps if Xerces wasn't slightly brain-damaged, we would
		// not need this
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				Thread.currentThread().setContextClassLoader(
					new JARClassLoader());
			}
		});
	} //}}}

	//{{{ initSystemProperties() method
	/**
	 * Load system properties.
	 */
	private static void initSystemProperties()
	{
		defaultProps = props = new Properties();

		try
		{
			loadProps(jEdit.class.getResourceAsStream(
				"/org/gjt/sp/jedit/jedit.props"),true);
			loadProps(jEdit.class.getResourceAsStream(
				"/org/gjt/sp/jedit/jedit_gui.props"),true);
			loadProps(jEdit.class.getResourceAsStream(
				"/org/gjt/sp/jedit/jedit_keys.props"),true);
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
				+ "jedit.jar is probably corrupt.");
			Log.log(Log.ERROR,jEdit.class,e);
			System.exit(1);
		}
	} //}}}

	//{{{ initSiteProperties() method
	/**
	 * Load site properties.
	 */
	private static void initSiteProperties()
	{
		// site properties are loaded as default properties, overwriting
		// jEdit's system properties

		String siteSettingsDirectory = MiscUtilities.constructPath(
			jEditHome, "properties");
		File siteSettings = new File(siteSettingsDirectory);

		if (!(siteSettings.exists() && siteSettings.isDirectory()))
			return;

		String[] snippets = siteSettings.list();
		if (snippets == null)
			return;

		MiscUtilities.quicksort(snippets,
			new MiscUtilities.StringICaseCompare());

		for (int i = 0; i < snippets.length; ++i)
		{
			String snippet = snippets[i];
			if(!snippet.toLowerCase().endsWith(".props"))
				continue;

			try
			{
				String path = MiscUtilities.constructPath(
					siteSettingsDirectory,snippet);
				Log.log(Log.DEBUG,jEdit.class,
					"Loading site snippet: " + path);

				loadProps(new FileInputStream(new File(path)),true);
			}
			catch(FileNotFoundException fnf)
			{
				Log.log(Log.DEBUG,jEdit.class,fnf);
			}
			catch(IOException e)
			{
				Log.log(Log.ERROR,jEdit.class,"Cannot load site snippet "
					+ snippet);
				Log.log(Log.ERROR,jEdit.class,e);
			}
		}
	} //}}}

	//{{{ initActions() method
	/**
	 * Load actions.
	 */
	private static void initActions()
	{
		actionSets = new Vector();

		Reader in = new BufferedReader(new InputStreamReader(
			jEdit.class.getResourceAsStream("actions.xml")));
		builtInActionSet = new ActionSet(jEdit.getProperty(
			"action-set.jEdit"));
		if(!loadActions("actions.xml",in,builtInActionSet))
			System.exit(1);
		addActionSet(builtInActionSet);
	} //}}}

	//{{{ initDockables() method
	/**
	 * Load info on jEdit's built-in dockable windows.
	 */
	private static void initDockables()
	{
		Reader in = new BufferedReader(new InputStreamReader(
			jEdit.class.getResourceAsStream("dockables.xml")));
		if(!DockableWindowManager.loadDockableWindows("dockables.xml",
			in,builtInActionSet))
			System.exit(1);
	} //}}}

	//{{{ initPlugins() method
	/**
	 * Loads plugins.
	 */
	private static void initPlugins()
	{
		if(jEditHome != null)
			loadPlugins(MiscUtilities.constructPath(jEditHome,"jars"));

		if(settingsDirectory != null)
		{
			File jarsDirectory = new File(settingsDirectory,"jars");
			if(!jarsDirectory.exists())
				jarsDirectory.mkdir();
			loadPlugins(jarsDirectory.getPath());
		}
	} //}}}

	//{{{ initUserProperties() method
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
				loadProps(new FileInputStream(file),false);
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
	} //}}}

	//{{{ initPLAF() method
	/**
	 * Sets the Swing look and feel.
	 */
	private static void initPLAF()
	{
		theme = new JEditMetalTheme();
		theme.propertiesChanged();
		MetalLookAndFeel.setCurrentTheme(theme);

		try
		{
			String lf = getProperty("lookAndFeel");
			if(lf != null && lf.length() != 0)
				UIManager.setLookAndFeel(lf);
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,jEdit.class,e);
		}

		UIDefaults defaults = UIManager.getDefaults();

		// give all Swing components our colors
		if(jEdit.getBooleanProperty("textColors"))
		{
			Color background = new javax.swing.plaf.ColorUIResource(
				jEdit.getColorProperty("view.bgColor"));
			Color foreground = new javax.swing.plaf.ColorUIResource(
				jEdit.getColorProperty("view.fgColor"));
			Color caretColor = new javax.swing.plaf.ColorUIResource(
				jEdit.getColorProperty("view.caretColor"));
			Color selectionColor = new javax.swing.plaf.ColorUIResource(
				jEdit.getColorProperty("view.selectionColor"));

			String[] prefixes = { "TextField", "TextArea", "List", "Table" };
			for(int i = 0; i < prefixes.length; i++)
			{
				String prefix = prefixes[i];
				defaults.put(prefix + ".disabledBackground",background);
				defaults.put(prefix + ".background",background);
				defaults.put(prefix + ".disabledForeground",foreground);
				defaults.put(prefix + ".foreground",foreground);
				defaults.put(prefix + ".caretForeground",caretColor);
				defaults.put(prefix + ".selectionForeground",foreground);
				defaults.put(prefix + ".selectionBackground",selectionColor);
				//defaults.put(prefix + ".inactiveForeground",foreground);
			}

			defaults.put("Tree.background",background);
			defaults.put("Tree.foreground",foreground);
			defaults.put("Tree.textBackground",background);
			defaults.put("Tree.textForeground",foreground);
			defaults.put("Tree.selectionForeground",foreground);
			defaults.put("Tree.selectionBackground",selectionColor);
		}

		defaults.remove("SplitPane.border");
		defaults.remove("SplitPaneDivider.border");
	} //}}}

	//{{{ runStartupScripts() method
	/**
	 * Runs scripts in the site startup directory, and user startup directory.
	 */
	private static void runStartupScripts(File directory)
	{
		if (!directory.isDirectory())
			return;

		String[] snippets = directory.list();
		if (snippets == null)
			return;

		MiscUtilities.quicksort(snippets,
			new MiscUtilities.StringICaseCompare());

		for(int i = 0; i < snippets.length; ++i)
		{
			String snippet = snippets[i];
			if(!snippet.toLowerCase().endsWith(".bsh"))
				continue;

			String path = new File(directory,snippet).getPath();

			BeanShell.runScript(null,path,null,false);
		}
	} //}}}

	//{{{ initProxy() method
	private static void initProxy()
	{
		boolean enabled = jEdit.getBooleanProperty("firewall.enabled");
		if (!enabled) {
			Log.log(Log.DEBUG, jEdit.class, "HTTP proxy disabled");
			System.getProperties().remove("proxySet");
			System.getProperties().remove("proxyHost");
			System.getProperties().remove("proxyPort");
			System.getProperties().remove("http.proxyHost");
			System.getProperties().remove("http.proxyPort");
			System.getProperties().remove("http.nonProxyHosts");
			Authenticator.setDefault(null);
		} else {
			// set proxy host
			String host = jEdit.getProperty("firewall.host");
			if (host == null)
				return;

			System.setProperty("http.proxyHost", host);
			Log.log(Log.DEBUG, jEdit.class, "HTTP proxy enabled: " + host);
			// set proxy port
			String port = jEdit.getProperty("firewall.port");
			if (port != null)
				System.setProperty("http.proxyPort", port);

			// set non proxy hosts list
			String nonProxyHosts = jEdit.getProperty("firewall.nonProxyHosts");
			if (nonProxyHosts != null)
				System.setProperty("http.nonProxyHosts", nonProxyHosts);

			// set proxy authentication
			String username = jEdit.getProperty("firewall.user");
			String password = jEdit.getProperty("firewall.password");

			// null not supported?
			if(password == null)
				password = "";

			if(username == null || username.length()==0)
			{
				Log.log(Log.DEBUG, jEdit.class, "HTTP proxy without user");
				Authenticator.setDefault(new FirewallAuthenticator(null));
			}
			else
			{
				Log.log(Log.DEBUG, jEdit.class, "HTTP proxy user: " + username);
				PasswordAuthentication pw = new PasswordAuthentication(
					username,password.toCharArray()
				);
				Authenticator.setDefault(new FirewallAuthenticator(pw));
			}
		}
	} //}}}

	//{{{ FirewallAuthenticator class
	static class FirewallAuthenticator extends Authenticator
	{
		PasswordAuthentication pw;

		public FirewallAuthenticator(PasswordAuthentication pw)
		{
			this.pw = pw;
		}

		protected PasswordAuthentication getPasswordAuthentication()
		{
			return pw;
		}
	} //}}}

	//{{{ getNotLoadedPluginJARs() method
	private static void getNotLoadedPluginJARs(Vector returnValue,
		String dir, String[] list)
	{
loop:		for(int i = 0; i < list.length; i++)
		{
			String name = list[i];
			if(!name.toLowerCase().endsWith(".jar"))
				continue loop;

			String path = MiscUtilities.constructPath(dir,name);

			for(int j = 0; j < jars.size(); j++)
			{
				EditPlugin.JAR jar = (EditPlugin.JAR)
					jars.elementAt(j);
				String jarPath = jar.getPath();
				String jarName = MiscUtilities.getFileName(jarPath);

				if(path.equals(jarPath))
					continue loop;
				else if(!new File(jarPath).exists()
					&& name.equals(jarName))
					continue loop;
			}

			returnValue.addElement(path);
		}
	} //}}}

	//{{{ gotoMarker() method
	private static void gotoMarker(final View view, final Buffer buffer,
		final String marker)
	{
		VFSManager.runInAWTThread(new Runnable()
		{
			public void run()
			{
				int pos;

				// Handle line number
				if(marker.startsWith("+line:"))
				{
					try
					{
						int line = Integer.parseInt(marker.substring(6));
						pos = buffer.getLineStartOffset(line - 1);
					}
					catch(Exception e)
					{
						return;
					}
				}
				// Handle marker
				else if(marker.startsWith("+marker:"))
				{
					if(marker.length() != 9)
						return;

					Marker m = buffer.getMarker(marker.charAt(8));
					if(m == null)
						return;
					pos = m.getPosition();
				}
				// Can't happen
				else
					throw new InternalError();

				if(view != null && view.getBuffer() == buffer)
					view.getTextArea().setCaretPosition(pos);
				else
					buffer.setIntegerProperty(Buffer.CARET,pos);
			}
		});
	} //}}}

	//{{{ addBufferToList() method
	private static void addBufferToList(Buffer buffer)
	{
		// if only one, clean, 'untitled' buffer is open, we
		// replace it
		if(viewCount <= 1 && buffersFirst != null
			&& buffersFirst == buffersLast
			&& buffersFirst.isUntitled()
			&& !buffersFirst.isDirty())
		{
			Buffer oldBuffersFirst = buffersFirst;
			buffersFirst = buffersLast = buffer;
			EditBus.send(new BufferUpdate(oldBuffersFirst,null,
				BufferUpdate.CLOSED));
			return;
		}

		bufferCount++;

		if(buffersFirst == null)
		{
			buffersFirst = buffersLast = buffer;
			return;
		}
		//{{{ Sort buffer list

		/**
		 * Note that we use either buffer.getPath() or buffer.toString().
		 * If the former, then the list is sorted first by directory,
		 * then by file name; if the latter, it's the opposite.
		 *
		 * (Buffer.toString() returns "NAME (DIR)")
		 */
		else if(sortBuffers)
		{
			String name1 = (sortByName ? buffer.toString()
				: buffer.getPath());

			Buffer _buffer = buffersFirst;
			while(_buffer != null)
			{
				String name2 = (sortByName ? _buffer.toString()
					: _buffer.getPath());
				if(MiscUtilities.compareStrings(name1,name2,true) <= 0)
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
		} //}}}

		buffer.prev = buffersLast;
		buffersLast.next = buffer;
		buffersLast = buffer;
	} //}}}

	//{{{ removeBufferFromList() method
	private static void removeBufferFromList(Buffer buffer)
	{
		synchronized(bufferListLock)
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

			// fixes the hang that can occur if we 'save as' to a new
			// filename which requires re-sorting
			buffer.next = buffer.prev = null;
		}
	} //}}}

	//{{{ addViewToList() method
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
	} //}}}

	//{{{ removeViewFromList() method
	private static void removeViewFromList(View view)
	{
		viewCount--;

		if(viewsFirst == viewsLast)
		{
			viewsFirst = viewsLast = null;
			return;
		}

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
	} //}}}

	//{{{ closeView() method
	/**
	 * closeView() used by exit().
	 */
	private static void closeView(View view, boolean callExit)
	{
		if(viewsFirst == viewsLast && callExit)
			exit(view,false); /* exit does editor event & save */
		else
		{
			EditBus.send(new ViewUpdate(view,ViewUpdate.CLOSED));

			view.close();
			removeViewFromList(view);
		}
	} //}}}

	//{{{ loadModeCatalog() method
	/**
	 * Loads a mode catalog file.
	 * @since jEdit 3.2pre2
	 */
	private static void loadModeCatalog(String path, boolean resource)
	{
		Log.log(Log.MESSAGE,jEdit.class,"Loading mode catalog file " + path);

		ModeCatalogHandler handler = new ModeCatalogHandler(
			MiscUtilities.getParentOfPath(path),resource);
		XmlParser parser = new XmlParser();
		parser.setHandler(handler);
		try
		{
			InputStream _in;
			if(resource)
				_in = jEdit.class.getResourceAsStream(path);
			else
				_in = new FileInputStream(path);
			BufferedReader in = new BufferedReader(
				new InputStreamReader(_in));
			parser.parse(null, null, in);
		}
		catch(XmlException xe)
		{
			int line = xe.getLine();
			String message = xe.getMessage();
			Log.log(Log.ERROR,jEdit.class,path + ":" + line
				+ ": " + message);
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,jEdit.class,e);
		}
	} //}}}

	//{{{ loadPlugins() method
	/**
	 * Loads all plugins in a directory.
	 * @param directory The directory
	 */
	private static void loadPlugins(String directory)
	{
		Log.log(Log.NOTICE,jEdit.class,"Loading plugins from "
			+ directory);

		File file = new File(directory);
		if(!(file.exists() && file.isDirectory()))
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

			String path = MiscUtilities.constructPath(directory,plugin);

			if(plugin.equals("EditBuddy.jar")
				|| plugin.equals("PluginManager.jar")
				|| plugin.equals("Firewall.jar")
				|| plugin.equals("Tidy.jar"))
			{
				pluginError(path,"plugin-error.obsolete",null);
				continue;
			}

			try
			{
				Log.log(Log.DEBUG,jEdit.class,
					"Scanning JAR file: " + path);
				new JARClassLoader(path);
			}
			catch(IOException io)
			{
				Log.log(Log.ERROR,jEdit.class,"Cannot load"
					+ " plugin " + plugin);
				Log.log(Log.ERROR,jEdit.class,io);

				String[] args = { io.toString() };
				pluginError(path,"plugin-error.load-error",args);
			}
		}
	} //}}}

	//{{{ initKeyBindings() method
	/**
	 * Loads all key bindings from the properties.
	 * @since 3.1pre1
	 */
	private static void initKeyBindings()
	{
		inputHandler.removeAllKeyBindings();

		EditAction[] actions = getActions();
		for(int i = 0; i < actions.length; i++)
		{
			EditAction action = actions[i];

			String shortcut1 = jEdit.getProperty(action.getName()
				+ ".shortcut");
			if(shortcut1 != null)
				inputHandler.addKeyBinding(shortcut1,action);

			String shortcut2 = jEdit.getProperty(action.getName()
				+ ".shortcut2");
			if(shortcut2 != null)
				inputHandler.addKeyBinding(shortcut2,action);
		}
	} //}}}

	//}}}
}
