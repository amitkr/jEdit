/*
 * GUIUtilities.java - Various GUI utility functions
 * Copyright (C) 1999 Slava Pestov
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

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.URL;
import java.util.StringTokenizer;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.syntax.SyntaxStyle;

/**
 * Class with several useful GUI functions.<p>
 *
 * It provides methods for:
 * <ul>
 * <li>Loading menu bars, menus and menu items from the properties
 * <li>Loading popup menus from the properties
 * <li>Loading tool bars and tool bar buttons from the properties
 * <li>Displaying various common dialog boxes
 * <li>Converting string representations of colors to color objects
 * <li>Loading and saving window geometry from the properties
 * <li>Displaying file open and save dialog boxes
 * </ul>
 *
 * @author Slava Pestov
 * @version $Id$
 */
public class GUIUtilities
{
	/**
	 * Loads a menubar from the properties for the specified view.
	 * @param view The view to load the menubar for
	 * @param name The property with the white space separated
	 * list of menus
	 */
	public static JMenuBar loadMenubar(View view, String name)
	{
		if(name == null)
			return null;
		JMenuBar mbar = new JMenuBar();
		String menus = jEdit.getProperty(name);
		if(menus != null)
		{
			StringTokenizer st = new StringTokenizer(menus);
			while(st.hasMoreTokens())
			{
				JMenu menu = loadMenu(view,st.nextToken());
				if(menu != null)
					mbar.add(menu);
			}
		}
		return mbar;
	}

	/**
	 * Loads a menu from the properties for the specified view.
	 * The white space separated list of menu items is obtained from
	 * the property named <code><i>name</i></code>. The menu label is
	 * obtained from the <code><i>name</i>.label</code> property.
	 * @param view The view to load the menu for
	 * @param name The menu name
	 */
	public static JMenu loadMenu(View view, String name)
	{
		if(name == null)
			return null;
		JMenu menu = view.getMenu(name);
		if(menu == null)
		{
			String label = jEdit.getProperty(name + ".label");
			if(label == null)
			{
				System.err.println("menu label is null: "
					+ name);
				return null;
			}
			int index = label.indexOf('$');
			if(index != -1 && label.length() - index > 1)
			{
				menu = new JMenu(label.substring(0,index)
					.concat(label.substring(++index)));
				menu.setMnemonic(Character.toLowerCase(label
					.charAt(index)));
			}
			else
				menu = new JMenu(label);
		}
		else
			return menu;
		String menuItems = jEdit.getProperty(name);
		if(menuItems != null)
		{
			StringTokenizer st = new StringTokenizer(menuItems);
			while(st.hasMoreTokens())
			{
				String menuItemName = st.nextToken();
				if(menuItemName.equals("-"))
					menu.addSeparator();
				else
				{
					JMenuItem mi = loadMenuItem(view,menuItemName);
					if(mi != null)
						menu.add(mi);
				}
			}
		}
		return menu;
	}

	/**
	 * Loads a popup menu from the properties for the specified view.
	 * @param view The view to load the popup menu for
	 * @param name The property with the white space separated list of
	 * menu items
	 */
	public static JPopupMenu loadPopupMenu(View view, String name)
	{
		JPopupMenu menu = new JPopupMenu();
		menu.setInvoker(view);
		String menuItems = jEdit.getProperty(name);
		if(menuItems != null)
		{
			StringTokenizer st = new StringTokenizer(menuItems);
			while(st.hasMoreTokens())
			{
				String menuItemName = st.nextToken();
				if(menuItemName.equals("-"))
					menu.addSeparator();
				else
				{
					JMenuItem mi = loadMenuItem(view,menuItemName);
					if(mi != null)
						menu.add(mi);
				}
			}
		}
		return menu;
	}

	/**
	 * Loads a menu item from the properties for the specified view.
	 * The menu label is obtained from the <code><i>name</i>.label</code>
	 * property. The keyboard shortcut is obtained from the
	 * <code><i>name</i>.shortcut</code> property.
	 * @param view The view to load the menu for
	 * @param name The menu item name
	 */
	public static JMenuItem loadMenuItem(View view, String name)
	{
		if(name.startsWith("%"))
			return loadMenu(view,name.substring(1));
		String arg;
		String action;
		int index = name.indexOf('@');
		if(index != -1)
		{
			arg = name.substring(index+1);
			action = name.substring(0,index);
		}
		else
		{
			arg = null;
			action = name;
		}
		JMenuItem mi;
                String label = jEdit.getProperty(name.concat(".label"));
		String keyStroke = jEdit.getProperty(name.concat(".shortcut"));
		if(label == null)
		{
			System.err.println("Menu item label is null: "
				+ name);
			return null;
		}

		index = label.indexOf('$');
                if(index != -1 && label.length() - index > 1)
		{
			mi = new EnhancedMenuItem(label.substring(0,index)
				.concat(label.substring(++index)),keyStroke);
                        mi.setMnemonic(Character.toLowerCase(label.charAt(index)));
		}
		else
			mi = new EnhancedMenuItem(label,keyStroke);
		
		EditAction a = jEdit.getAction(action);
		if(a == null)
			mi.setEnabled(false);
		else
			mi.addActionListener(a);
		mi.setActionCommand(arg);
		return mi;
	}

	/**
	 * Loads a toolbar from the properties.
	 * @param name The property with the white space separated list
	 * of tool bar buttons
	 */
	public static Component loadToolBar(String name)
	{
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);

		String buttons = jEdit.getProperty(name);
		if(buttons == null)
			return null;

		StringTokenizer st = new StringTokenizer(buttons);
		while(st.hasMoreElements())
		{
			String buttonName = st.nextToken();
			if(buttonName.equals("-"))
				toolBar.addSeparator();
			else
			{
				JButton button = loadToolButton(buttonName);
				if(button != null)
					toolBar.add(button);
			}
		}

		toolBar.add(Box.createHorizontalGlue());

		return toolBar;
	}

	/**
	 * Loads a tool bar button. The tooltip is constructed from
	 * the <code><i>name</i>.label</code> and
	 * <code><i>name</i>.shortcut</code> properties and the icon is loaded
	 * from the resource named '/org/gjt/sp/jedit/toolbar/' suffixed
	 * with the value of the <code><i>name</i>.icon</code> property.
	 * @param name The name of the button
	 */
	public static JButton loadToolButton(String name)
	{
		String iconName = jEdit.getProperty(name + ".icon");
		if(iconName == null)
		{
			System.out.println("Tool button icon is null: " + name);
			return null;
		}
		URL url = GUIUtilities.class.getResource("toolbar/" + iconName);
		if(url == null)
		{
			System.out.println("Tool button icon is null: " + name);
			return null;
		}
		JButton button = new JButton(new ImageIcon(url));
		button.setRequestFocusEnabled(false);

		String toolTip = jEdit.getProperty(name + ".label");
		if(toolTip == null)
		{
			System.out.println("Tool button label is null: " + name);
			return null;
		}
		toolTip = prettifyMenuLabel(toolTip);
		String shortcut = jEdit.getProperty(name + ".shortcut");
		if(shortcut != null)
			toolTip = toolTip + " (" + shortcut + ")";
		button.setToolTipText(toolTip);

		int index = name.indexOf('@');
		String actionCommand;
		if(index != -1)
		{
			actionCommand = name.substring(index + 1);
			name = name.substring(0,index);
		}
		else
			actionCommand = null;
		EditAction action = jEdit.getAction(name);
		if(action == null)
			button.setEnabled(false);
		else
		{
			button.addActionListener(action);
			button.setActionCommand(actionCommand);
		}
		return button;
	}

	/**
	 * `Prettyfies' a menu item label by removing the `$' sign and the
	 * training ellipisis, if any. This can be used to process the
	 * contents of an <i>action</i>.label property.
	 */
	public static String prettifyMenuLabel(String label)
	{
		int index = label.indexOf('$');
		if(index != -1)
		{
			label = label.substring(0,index)
				.concat(label.substring(index + 1));
		}
		if(label.endsWith("..."))
			label = label.substring(0,label.length() - 3);
		return label;
	}

	/**
	 * Displays a dialog box.
	 * The title of the dialog is fetched from
	 * the <code><i>name</i>.title</code> property. The message is fetched
	 * from the <code><i>name</i>.message</code> property. The message
	 * is formatted by the property manager with <code>args</code> as
	 * positional parameters.
	 * @param frame The frame to display the dialog for
	 * @param name The name of the dialog
	 * @param args Positional parameters to be substituted into the
	 * message text
	 */
	public static void message(Frame frame, String name, Object[] args)
	{
		hideSplashScreen();

		JOptionPane.showMessageDialog(frame,
			jEdit.getProperty(name.concat(".message"),args),
			jEdit.getProperty(name.concat(".title"),args),
			JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * Displays an error dialog box.
	 * The title of the dialog is fetched from
	 * the <code><i>name</i>.title</code> property. The message is fetched
	 * from the <code><i>name</i>.message</code> property. The message
	 * is formatted by the property manager with <code>args</code> as
	 * positional parameters.
	 * @param frame The frame to display the dialog for
	 * @param name The name of the dialog
	 * @param args Positional parameters to be substituted into the
	 * message text
	 */
	public static void error(Frame frame, String name, Object[] args)
	{
		hideSplashScreen();

		JOptionPane.showMessageDialog(frame,
			jEdit.getProperty(name.concat(".message"),args),
			jEdit.getProperty(name.concat(".title"),args),
			JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Displays an input dialog box and returns any text the user entered.
	 * The title of the dialog is fetched from
	 * the <code><i>name</i>.title</code> property. The message is fetched
	 * from the <code><i>name</i>.message</code> property.
	 * @param frame The frame to display the dialog for
	 * @param name The name of the dialog
	 * @param def The text to display by default in the input field
	 */
	public static String input(Frame frame, String name, Object def)
	{
		hideSplashScreen();

		String retVal = (String)JOptionPane.showInputDialog(frame,
			jEdit.getProperty(name.concat(".message")),
			jEdit.getProperty(name.concat(".title")),
			JOptionPane.QUESTION_MESSAGE,null,null,def);
		return retVal;
	}

	/**
	 * Displays an input dialog box and returns any text the user entered.
	 * The title of the dialog is fetched from
	 * the <code><i>name</i>.title</code> property. The message is fetched
	 * from the <code><i>name</i>.message</code> property.
	 * @param frame The frame to display the dialog for
	 * @param name The name of the dialog
	 * @param def The property whose text to display in the input field
	 */
	public static String inputProperty(Frame frame, String name, String def)
	{
		hideSplashScreen();

		String retVal = (String)JOptionPane.showInputDialog(frame,
			jEdit.getProperty(name.concat(".message")),
			jEdit.getProperty(name.concat(".title")),
			JOptionPane.QUESTION_MESSAGE,
			null,null,jEdit.getProperty(def));
		if(retVal != null)
			jEdit.setProperty(def,retVal);
		return retVal;
	}

	/**
	 * Displays a file selection dialog box. This is better than creating
	 * your own <code>JFileChooser</code> because it supports file filters,
	 * and reuses existing file choosers for maximum efficency.
	 * @param view The view
	 * @param file The file to select by default
	 * @param type The dialog type
	 * @return The selected file
	 */
	public static String showFileDialog(View view, String file, int type)
	{
		File _file = new File(file);
		JFileChooser chooser = getFileChooser();

		chooser.setCurrentDirectory(_file);
		chooser.setSelectedFile(_file);
		chooser.setDialogType(type);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

		int retVal = chooser.showDialog(view,null);
		if(retVal == JFileChooser.APPROVE_OPTION)
			return chooser.getSelectedFile().getAbsolutePath();
		else
			return null;
	}

	/**
	 * Converts a color name to a color object. The name must either be
	 * a known string, such as `red', `green', etc (complete list is in
	 * the <code>java.awt.Color</code> class) or a hex color value
	 * prefixed with `#', for example `#ff0088'.
	 * @param name The color name
	 */
	public static Color parseColor(String name)
	{
		if(name == null)
			return Color.black;
		else if(name.startsWith("#"))
		{
			try
			{
				return Color.decode(name);
			}
			catch(NumberFormatException nf)
			{
				return Color.black;
			}
		}
		else if("red".equals(name))
			return Color.red;
		else if("green".equals(name))
			return Color.green;
		else if("blue".equals(name))
			return Color.blue;
		else if("yellow".equals(name))
			return Color.yellow;
		else if("orange".equals(name))
			return Color.orange;
		else if("white".equals(name))
			return Color.white;
		else if("lightGray".equals(name))
			return Color.lightGray;
		else if("gray".equals(name))
			return Color.gray;
		else if("darkGray".equals(name))
			return Color.darkGray;
		else if("black".equals(name))
			return Color.black;
		else if("cyan".equals(name))
			return Color.cyan;
		else if("magenta".equals(name))
			return Color.magenta;
		else if("pink".equals(name))
			return Color.pink;
		else
			return Color.black;
	}

	/**
	 * Converts a color object to its hex value. The hex value
	 * prefixed is with `#', for example `#ff0088'.
	 * @param c The color object
	 */
	public static String getColorHexString(Color c)
	{
		String colString = Integer.toHexString(c.getRGB() & 0xffffff);
		return "#000000".substring(0,7 - colString.length()).concat(colString);
	}

	/**
	 * Converts a style string to a style object.
	 * @param str The style string
	 * @exception IllegalArgumentException if the style is invalid
	 */
	public static SyntaxStyle parseStyle(String str)
		throws IllegalArgumentException
	{
		Color color = Color.black;
		boolean italic = false;
		boolean bold = false;
		StringTokenizer st = new StringTokenizer(str);
		while(st.hasMoreTokens())
		{
			String s = st.nextToken();
			if(s.startsWith("color:"))
			{
				color = GUIUtilities.parseColor(s.substring(6));
			}
			else if(s.startsWith("style:"))
			{
				for(int i = 6; i < s.length(); i++)
				{
					if(s.charAt(i) == 'i')
						italic = true;
					else if(s.charAt(i) == 'b')
						bold = true;
					else
						throw new IllegalArgumentException(
							"Invalid style: " + s);
				}
			}
			else
				throw new IllegalArgumentException(
					"Invalid directive: " + s);
		}
		return new SyntaxStyle(color,italic,bold);
	}

	/**
	 * Converts a style into it's string representation.
	 * @param style The style
	 */
	public static String getStyleString(SyntaxStyle style)
	{
		StringBuffer buf = new StringBuffer();

		buf.append("color:" + getColorHexString(style.getColor()));
		if(!style.isPlain())
		{
			buf.append(" style:" + (style.isItalic() ? "i" : "")
				+ (style.isBold() ? "b" : ""));
		}

		return buf.toString();
	}

	/**
	 * Loads a windows's geometry from the properties.
	 * The geometry is loaded from the <code><i>name</i>.x</code>,
	 * <code><i>name</i>.y</code>, <code><i>name</i>.width</code> and
	 * <code><i>name</i>.height</code> properties.
	 *
	 * @param win The window
	 * @param name The window name
	 */
	public static void loadGeometry(Window win, String name)
	{
		if(!"on".equals(jEdit.getProperty("saveGeometry")))
			return;

		int x, y, width, height;

		try
		{
			width = Integer.parseInt(jEdit.getProperty(name + ".width"));
			height = Integer.parseInt(jEdit.getProperty(name + ".height"));
		}
		catch(NumberFormatException nf)
		{
			Dimension size = win.getSize();
			width = size.width;
			height = size.height;
		}

		try
		{
			x = Integer.parseInt(jEdit.getProperty(name + ".x"));
			y = Integer.parseInt(jEdit.getProperty(name + ".y"));
		}
		catch(NumberFormatException nf)
		{
			Dimension screen = win.getToolkit().getScreenSize();
			x = (screen.width - width) / 2;
			y = (screen.height - height) / 2;
		}

		win.setLocation(x,y);
		win.setSize(width,height);
	}

	/**
	 * Saves a window's geometry to the properties.
	 * The geometry is saved to the <code><i>name</i>.x</code>,
	 * <code><i>name</i>.y</code>, <code><i>name</i>.width</code> and
	 * <code><i>name</i>.height</code> properties.
	 * @param win The window
	 * @param name The window name
	 */
	public static void saveGeometry(Window win, String name)
	{
		if(!"on".equals(jEdit.getProperty("saveGeometry")))
			return;

		Point location = win.getLocation();
		Dimension size = win.getSize();
		jEdit.setProperty(name + ".x",String.valueOf(location.x));
		jEdit.setProperty(name + ".y",String.valueOf(location.y));
		jEdit.setProperty(name + ".width",String.valueOf(size.width));
		jEdit.setProperty(name + ".height",String.valueOf(size.height));

	}

	/**
	 * Shows the wait cursor.
	 * @param view The view
	 */
	public static void showWaitCursor(View view)
	{
		Cursor cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
		view.setCursor(cursor);
		view.getTextArea().getPainter().setCursor(cursor);
	}

	/**
	 * Hides the wait cursor.
	 * @param view The view
	 */
	public static void hideWaitCursor(View view)
	{
		Cursor cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
		view.setCursor(cursor);
		Cursor text = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
		view.getTextArea().getPainter().setCursor(text);
	}

	/**
	 * Ensures that the splash screen is not visible. This should be
	 * called before displaying any dialog boxes or windows at
	 * startup.
	 */
	public static void hideSplashScreen()
	{
		if(splash != null)
		{
			splash.dispose();
			splash = null;
		}
	}

	// package-private members

	static void showSplashScreen()
	{
		splash = new SplashScreen();
	}

	// private members
	private static SplashScreen splash;
	private static JFileChooser chooser;

	private GUIUtilities() {}

	// Since only one file chooser is every visible at any one
	// time, we can reuse 'em
	private static JFileChooser getFileChooser()
	{
		if(chooser == null)
		{
			chooser = new JFileChooser();

			if("on".equals(jEdit.getProperty("filefilters")))
			{
				REFileFilter[] filters = jEdit.getFileFilters();
				for(int i = 0; i < filters.length; i++)
				{
					chooser.addChoosableFileFilter(filters[i]);
				}

				chooser.setFileFilter(chooser.getAcceptAllFileFilter());
			}
		}

		return chooser;
	}
}

/*
 * ChangeLog:
 * $Log$
 * Revision 1.30  1999/10/05 04:43:58  sp
 * Minor bug fixes and updates
 *
 * Revision 1.29  1999/10/04 03:20:50  sp
 * Option pane change, minor tweaks and bug fixes
 *
 * Revision 1.28  1999/09/30 12:21:04  sp
 * No net access for a month... so here's one big jEdit 2.1pre1
 *
 * Revision 1.27  1999/07/08 06:35:41  sp
 * 1.7pre5, yay
 *
 * Revision 1.26  1999/07/08 06:06:04  sp
 * Bug fixes and miscallaneous updates
 *
 * Revision 1.25  1999/07/05 04:38:39  sp
 * Massive batch of changes... bug fixes, also new text component is in place.
 * Have fun
 *
 * Revision 1.24  1999/06/22 06:14:39  sp
 * RMI updates, text area updates, flag to disable geometry saving
 *
 * Revision 1.23  1999/06/16 03:29:59  sp
 * Added <title> tags to docs, configuration data is now stored in a
 * ~/.jedit directory, style option pane finished
 *
 * Revision 1.22  1999/06/13 05:47:02  sp
 * Minor changes required for LatestVersion plugin
 *
 * Revision 1.21  1999/06/12 02:30:27  sp
 * Find next can now perform multifile searches, multifile-search command added,
 * new style option pane
 *
 * Revision 1.20  1999/05/27 00:02:50  sp
 * Documentation updates, minor tweaks for WWW browser command unbundling
 *
 */
