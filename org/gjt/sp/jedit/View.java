/*
 * View.java - jEdit view
 * Copyright (C) 1998, 1999 Slava Pestov
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

import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import org.gjt.sp.jedit.msg.*;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.search.SearchAndReplace;
import org.gjt.sp.jedit.syntax.SyntaxStyle;
import org.gjt.sp.jedit.syntax.Token;
import org.gjt.sp.jedit.textarea.*;
import org.gjt.sp.util.Log;

/**
 * A window that edits buffers. There is no public constructor in the
 * View class. Views are created and destroyed by the <code>jEdit</code>
 * class.
 *
 * @author Slava Pestov
 * @version $Id$
 */
public class View extends JFrame implements EBComponent
{
	/**
	 * Reloads various settings from the properties.
	 */
	public void propertiesChanged()
	{
		if("on".equals(jEdit.getProperty("view.showToolbar")))
		{
			if(toolBar == null)
			{
				toolBar = GUIUtilities.loadToolBar("view.toolbar");
				toolBar.add(Box.createHorizontalStrut(10));
				toolBar.add(new JLabel(jEdit.getProperty("view.quicksearch")));
				Box box = new Box(BoxLayout.Y_AXIS);
				box.add(Box.createVerticalGlue());
				box.add(quicksearch);
				box.add(Box.createVerticalGlue());
				toolBar.add(box);
			}
			if(toolBar.getParent() == null)
				addToolBar(toolBar);
		}
		else if(toolBar != null)
		{
			removeToolBar(toolBar);
			toolBar = null;
			validate();
		}

		showFullPath = "on".equals(jEdit.getProperty("view.showFullPath"));

		String family = jEdit.getProperty("view.font");
		int size;
		try
		{
			size = Integer.parseInt(jEdit.getProperty(
				"view.fontsize"));
		}
		catch(NumberFormatException nf)
		{
			size = 14;
		}
		int style;
		try
		{
			style = Integer.parseInt(jEdit.getProperty(
				"view.fontstyle"));
		}
		catch(NumberFormatException nf)
		{
			style = Font.PLAIN;
		}
		Font font = new Font(family,style,size);

		TextAreaPainter painter = textArea.getPainter();

		painter.setFont(font);
		painter.setLineHighlightEnabled("on".equals(jEdit.getProperty(
			"view.lineHighlight")));
		painter.setLineHighlightColor(GUIUtilities.parseColor(
			jEdit.getProperty("view.lineHighlightColor")));
		painter.setBracketHighlightEnabled("on".equals(jEdit.getProperty(
			"view.bracketHighlight")));
		painter.setBracketHighlightColor(GUIUtilities.parseColor(
			jEdit.getProperty("view.bracketHighlightColor")));
		painter.setEOLMarkerEnabled("on".equals(jEdit.getProperty(
			"view.eolMarkers")));
		painter.setEOLMarkerColor(GUIUtilities.parseColor(
			jEdit.getProperty("view.eolMarkerColor")));
		painter.setCaretColor(GUIUtilities.parseColor(
			jEdit.getProperty("view.caretColor")));
		painter.setSelectionColor(GUIUtilities.parseColor(
			jEdit.getProperty("view.selectionColor")));
		painter.setBackground(GUIUtilities.parseColor(
			jEdit.getProperty("view.bgColor")));
		painter.setForeground(GUIUtilities.parseColor(
			jEdit.getProperty("view.fgColor")));
		painter.setBlockCaretEnabled("on".equals(jEdit.getProperty(
			"view.blockCaret")));

		textArea.setCaretBlinkEnabled("on".equals(jEdit.getProperty(
			"view.caretBlink")));

		textArea.putClientProperty(InputHandler.SMART_HOME_END_PROPERTY,
			new Boolean("yes".equals(jEdit.getProperty("view.homeEnd"))));
		try
		{
			textArea.setElectricScroll(Integer.parseInt(jEdit
				.getProperty("view.electricBorders")));
		}
		catch(NumberFormatException nf)
		{
			textArea.setElectricScroll(0);
		}

		loadStyles();

		updateOpenRecentMenu();
	}
	
	/**
	 * Recreates the buffers menu.
	 */
	public void updateBuffersMenu()
	{
		// Because the buffers menu contains normal items as
		// well as dynamically-generated stuff, we are careful
		// to only remove the dynamic crap here...
		for(int i = buffers.getMenuComponentCount() - 1; i >= 0; i--)
		{
			if(buffers.getMenuComponent(i) instanceof JSeparator)
				break;
			else
				buffers.remove(i);
		}

		ButtonGroup grp = new ButtonGroup();
		Buffer[] bufferArray = jEdit.getBuffers();
		for(int i = 0; i < bufferArray.length; i++)
		{
			Buffer b = bufferArray[i];
			String name = b.getPath();
			Object[] args = { name,
				new Integer(b.isReadOnly() ? 1: 0),
				new Integer(b.isDirty() ? 1 : 0),
				new Integer(b.isNewFile() ? 1 : 0) };
			JRadioButtonMenuItem menuItem =
				new JRadioButtonMenuItem(jEdit.getProperty(
					"view.title",args));
			menuItem.addActionListener(jEdit.getAction("select-buffer"));
			grp.add(menuItem);
			menuItem.setActionCommand(name);
			if(buffer == b)
				menuItem.getModel().setSelected(true);
			buffers.add(menuItem);
		}
	}
	
	/**
	 * Recreates the open recent menu.
	 */
	public void updateOpenRecentMenu()
	{
		if(openRecent.getMenuComponentCount() != 0)
			openRecent.removeAll();
		EditAction action = jEdit.getAction("open-path");
		String[] recentArray = jEdit.getRecent();
		if(recentArray.length == 0)
		{
			openRecent.add(GUIUtilities.loadMenuItem(this,"no-recent"));
			return;
		}
		for(int i = 0; i < recentArray.length; i++)
		{
			String path = recentArray[i];
			EnhancedMenuItem menuItem = new EnhancedMenuItem(path,
				null,action,path);
			openRecent.add(menuItem);
		}
	}
	
	/**
	 * Recreates the goto marker and clear marker menus.
	 */
	public void updateMarkerMenus()
	{
		if(clearMarker.getMenuComponentCount() != 0)
			clearMarker.removeAll();
		if(gotoMarker.getMenuComponentCount() != 0)
			gotoMarker.removeAll();
		EditAction clearMarkerAction = jEdit.getAction("clear-marker");
		EditAction gotoMarkerAction = jEdit.getAction("goto-marker");
		Enumeration enum = buffer.getMarkers();
		if(!enum.hasMoreElements())
		{
			clearMarker.add(GUIUtilities.loadMenuItem(this,"no-markers"));
			gotoMarker.add(GUIUtilities.loadMenuItem(this,"no-markers"));
			return;
		}
		while(enum.hasMoreElements())
		{
			String name = ((Marker)enum.nextElement())
				.getName();
			EnhancedMenuItem menuItem = new EnhancedMenuItem(name,
				null,clearMarkerAction,name);
			clearMarker.add(menuItem);
			menuItem = new EnhancedMenuItem(name,null,
				gotoMarkerAction,name);
			gotoMarker.add(menuItem);
		}
	}

	/**
	 * Recreates the macros menu.
	 */
	public void updateMacrosMenu()
	{
		// Because the macros menu contains normal items as
		// well as dynamically-generated stuff, we are careful
		// to only remove the dynamic crap here...
		for(int i = macros.getMenuComponentCount() - 1; i >= 0; i--)
		{
			if(macros.getMenuComponent(i) instanceof JSeparator)
				break;
			else
				macros.remove(i);
		}

		int count = macros.getMenuComponentCount();

		Vector macroVector = Macros.getMacros();
		createMacrosMenu(macros,macroVector,0);

		if(count == macros.getMenuComponentCount())
			macros.add(GUIUtilities.loadMenuItem(this,"no-macros"));
	}

	/**
	 * Recreates the plugins menu.
	 */
	public void updatePluginsMenu()
	{
		if(plugins.getMenuComponentCount() != 0)
			plugins.removeAll();

		// Query plugins for menu items
		Vector pluginMenus = new Vector();
		Vector pluginMenuItems = new Vector();

		EditPlugin[] pluginArray = jEdit.getPlugins();
		for(int i = 0; i < pluginArray.length; i++)
			pluginArray[i].createMenuItems(this,pluginMenus,pluginMenuItems);

		if(pluginMenus.isEmpty() && pluginMenuItems.isEmpty())
		{
			plugins.add(GUIUtilities.loadMenuItem(this,"no-plugins"));
			return;
		}

		// Sort them
		MenuItemCompare comp = new MenuItemCompare();
		MiscUtilities.quicksort(pluginMenus,comp);
		MiscUtilities.quicksort(pluginMenuItems,comp);

		for(int i = 0; i < pluginMenus.size(); i++)
			plugins.add((JMenu)pluginMenus.elementAt(i));

		if(!pluginMenus.isEmpty() && !pluginMenuItems.isEmpty())
			plugins.addSeparator();

		for(int i = 0; i < pluginMenuItems.size(); i++)
			plugins.add((JMenuItem)pluginMenuItems.elementAt(i));
	}

	private static class MenuItemCompare implements MiscUtilities.Compare
	{
		public int compare(Object obj1, Object obj2)
		{
			return ((JMenuItem)obj1).getText().compareTo(
				((JMenuItem)obj2).getText());
		}
	}

	/**
	 * Displays the specified string in the status area of this view.
	 * @param str The string to display
	 */
	public void showStatus(String str)
	{
		status.showStatus(str);
	}

	/**
	 * Updates the title bar and read only status of the text
	 * area.
	 */
	public void updateTitle()
	{
		Object[] args = { ((showFullPath && !buffer.isNewFile())
			? buffer.getPath() : buffer.getName()),
			new Integer(buffer.isReadOnly() ? 1 : 0),
			new Integer(buffer.isDirty() ? 1: 0),
			new Integer(buffer.isNewFile() ? 1: 0)};
		setTitle(jEdit.getProperty("view.title",args));
		textArea.setEditable(!buffer.isReadOnly());
	}

	/**
	 * Returns the buffer being edited by this view.
	 */
	public final Buffer getBuffer()
	{
		return buffer;
	}

	/**
	 * Sets the buffer being edited by this view.
	 * @param buffer The buffer to edit.
	 */
	public void setBuffer(Buffer buffer)
	{
		if(this.buffer == buffer)
			return;

		if(buffer == null)
			throw new NullPointerException("Not supported yet");

		Buffer oldBuffer = this.buffer;
		if(oldBuffer != null)
		{
			saveCaretInfo();
			Mode mode = oldBuffer.getMode();
			if(mode != null)
				mode.leaveView(this);
		}
		this.buffer = buffer;

		textArea.setDocument(buffer);

		int start = Math.min(buffer.getLength(),buffer.getSavedSelStart());
		int end = Math.min(buffer.getLength(),buffer.getSavedSelEnd());
		textArea.select(start,end);
		textArea.setSelectionRectangular(buffer.isSelectionRectangular());

		updateMarkerMenus();
		updateTitle();
		status.repaint();

		Mode mode = buffer.getMode();
		if(mode != null)
			mode.enterView(this);

		focusOnTextArea();

		// Fire event
		EditBus.send(new ViewUpdate(this,ViewUpdate.BUFFER_CHANGED));

		updateBuffersMenu();
	}

	/**
	 * Sets the focus onto the text area.
	 */
	public final void focusOnTextArea()
	{
		textArea.requestFocus();
	}

	/**
	 * Returns the view's quicksearch text field, or null if the toolbar
	 * is not showing.
	 */
	public final HistoryTextField getQuickSearch()
	{
		if(quicksearch.isShowing())
			return quicksearch;
		else
			return null;
	}

	/**
	 * Returns the view's text area.
	 */
	public final JEditTextArea getTextArea()
	{
		return textArea;
	}

	/**
	 * Adds a tool bar to this view.
	 * @param toolBar The tool bar
	 */
	public void addToolBar(Component toolBar)
	{
		toolBars.add(toolBar);
		invalidate();
		validate();
	}

	/**
	 * Removes a tool bar from this view.
	 * @param toolBar The tool bar
	 */
	public void removeToolBar(Component toolBar)
	{
		toolBars.remove(toolBar);
	}

	/**
	 * Saves the caret information to the current buffer.
	 */
	public void saveCaretInfo()
	{
		buffer.setCaretInfo(
			textArea.getSelectionStart(),
			textArea.getSelectionEnd(),
			textArea.isSelectionRectangular());
	}

	/**
	 * Returns true if this view has been closed with
	 * <code>jEdit.closeView()</code>.
	 */
	public final boolean isClosed()
	{
		return closed;
	}

	/**
	 * Shows the wait cursor.
	 */
	public void showWaitCursor()
	{
		if(waitCount++ == 0)
		{
			Cursor cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
			setCursor(cursor);
			getTextArea().getPainter().setCursor(cursor);
		}
	}

	/**
	 * Hides the wait cursor.
	 */
	public void hideWaitCursor()
	{
		if(waitCount > 0)
			waitCount--;

		if(waitCount == 0)
		{
			Cursor cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
			setCursor(cursor);
			cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
			getTextArea().getPainter().setCursor(cursor);
		}
	}

	/**
	 * Returns the next view in the list.
	 */
	public final View getNext()
	{
		return next;
	}

	/**
	 * Returns the previous view in the list.
	 */
	public final View getPrev()
	{
		return prev;
	}

	public void handleMessage(EBMessage msg)
	{
		if(msg instanceof PropertiesChanged)
			propertiesChanged();
		else if(msg instanceof MacrosChanged)
			updateMacrosMenu();
		else if(msg instanceof BufferUpdate)
			handleBufferUpdate((BufferUpdate)msg);
	}

	// package-private members
	View prev;
	View next;

	View(View view, Buffer buffer)
	{
		setIconImage(GUIUtilities.getEditorIcon());

		// Dynamic menus
		buffers = GUIUtilities.loadMenu(this,"buffers");
		openRecent = GUIUtilities.loadMenu(this,"open-recent");
		clearMarker = GUIUtilities.loadMenu(this,"clear-marker");
		gotoMarker = GUIUtilities.loadMenu(this,"goto-marker");
		macros = GUIUtilities.loadMenu(this,"macros");
		updateMacrosMenu();
		plugins = GUIUtilities.loadMenu(this,"plugins");
		updatePluginsMenu();

		EditBus.addToBus(this);

		setJMenuBar(GUIUtilities.loadMenubar(this,"view.mbar"));

		toolBars = new Box(BoxLayout.Y_AXIS);

		quicksearch = new HistoryTextField("find");
		Dimension dim = quicksearch.getPreferredSize();
		dim.width = Integer.MAX_VALUE;
		quicksearch.setMaximumSize(dim);
		quicksearch.addActionListener(new ActionHandler());

		textArea = new JEditTextArea();

		// Add the line number display
		textArea.add(JEditTextArea.LEFT_OF_SCROLLBAR,status = new StatusBar());

		// Set up the right-click popup menu
		textArea.setRightClickPopup(GUIUtilities
			.loadPopupMenu(this,"view.context"));

		textArea.setInputHandler(jEdit.getInputHandler().copy());
		textArea.addCaretListener(new CaretHandler());

		propertiesChanged();

		Buffer[] bufferArray = jEdit.getBuffers();
		if(buffer == null)
			setBuffer(bufferArray[bufferArray.length - 1]);
		else
			setBuffer(buffer);

		getContentPane().add(BorderLayout.NORTH,toolBars);
		getContentPane().add(BorderLayout.CENTER,textArea);

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
	}

	JMenu getMenu(String name)
	{
		if(name.equals("buffers"))
			return buffers;
		else if(name.equals("open-recent"))
			return openRecent;
		else if(name.equals("clear-marker"))
			return clearMarker;
		else if(name.equals("goto-marker"))
			return gotoMarker;
		else if(name.equals("macros"))
			return macros;
		else if(name.equals("plugins"))
			return plugins;
		else
			return null;
	}
	
	void close()
	{
		closed = true;
		GUIUtilities.saveGeometry(this,"view");
		saveCaretInfo();
		EditBus.removeFromBus(this);
		dispose();
	}

	// private members
	private Buffer buffer;
	private boolean closed;

	private JMenu buffers;
	private JMenu openRecent;
	private JMenu clearMarker;
	private JMenu gotoMarker;
	private JMenu macros;
	private JMenu plugins;

	private Box toolBars;
	private JToolBar toolBar;
	private HistoryTextField quicksearch;
	private JEditTextArea textArea;
	private StatusBar status;

	private int waitCount;

	private boolean showFullPath;

	private void createMacrosMenu(JMenu menu, Vector vector, int start)
	{
		EditAction action = jEdit.getAction("play-macro");

		for(int i = start; i < vector.size(); i++)
		{
			Object obj = vector.elementAt(i);
			if(obj instanceof Macros.Macro)
			{
				Macros.Macro macro = (Macros.Macro)obj;
				String label = macro.label;
				String path = macro.path;
				EnhancedMenuItem menuItem = new EnhancedMenuItem(
					label,jEdit.getProperty(macro.name + ".shortcut"),
					action,path);
				menu.add(menuItem);
			}
			else if(obj instanceof Vector)
			{
				Vector subvector = (Vector)obj;
				String name = (String)subvector.elementAt(0);
				JMenu submenu = new JMenu(name);
				createMacrosMenu(submenu,subvector,1);
				if(submenu.getMenuComponentCount() == 0)
				{
					submenu.add(GUIUtilities.loadMenuItem(
						this,"no-macros"));
				}
				menu.add(submenu);
			}
		}
	}

	private void loadStyles()
	{
		try
		{
			SyntaxStyle[] styles = new SyntaxStyle[Token.ID_COUNT];

			styles[Token.COMMENT1] = GUIUtilities.parseStyle(
				jEdit.getProperty("view.style.comment1"));
			styles[Token.COMMENT2] = GUIUtilities.parseStyle(
				jEdit.getProperty("view.style.comment2"));
			styles[Token.KEYWORD1] = GUIUtilities.parseStyle(
				jEdit.getProperty("view.style.keyword1"));
			styles[Token.KEYWORD2] = GUIUtilities.parseStyle(
				jEdit.getProperty("view.style.keyword2"));
			styles[Token.KEYWORD3] = GUIUtilities.parseStyle(
				jEdit.getProperty("view.style.keyword3"));
			styles[Token.LABEL] = GUIUtilities.parseStyle(
				jEdit.getProperty("view.style.label"));
			styles[Token.LITERAL1] = GUIUtilities.parseStyle(
				jEdit.getProperty("view.style.literal1"));
			styles[Token.LITERAL2] = GUIUtilities.parseStyle(
				jEdit.getProperty("view.style.literal2"));
			styles[Token.OPERATOR] = GUIUtilities.parseStyle(
				jEdit.getProperty("view.style.operator"));
			styles[Token.INVALID] = GUIUtilities.parseStyle(
				jEdit.getProperty("view.style.invalid"));

			textArea.getPainter().setStyles(styles);
		}
		catch(Exception e)
		{
			Log.log(Log.ERROR,this,e);
		}
	}

	private void handleBufferUpdate(BufferUpdate msg)
	{
		Buffer _buffer = msg.getBuffer();
		if(msg.getWhat() == BufferUpdate.CREATED)
			updateBuffersMenu();
		else if(msg.getWhat() == BufferUpdate.CLOSED)
		{
			if(_buffer == buffer)
			{
				Buffer[] bufferArray = jEdit.getBuffers();
				if(bufferArray.length != 0)
					setBuffer(bufferArray[bufferArray.length - 1]);
			}

			updateOpenRecentMenu();
			updateBuffersMenu();
		}
		else if(msg.getWhat() == BufferUpdate.DIRTY_CHANGED)
		{
			if(_buffer == buffer)
				updateTitle();
			updateBuffersMenu();
		}
		else if(msg.getWhat() == BufferUpdate.MARKERS_CHANGED)
		{
			if(_buffer == buffer)
				updateMarkerMenus();
		}
		else if(msg.getWhat() == BufferUpdate.MODE_CHANGED)
		{
			if(_buffer == buffer)
				textArea.getPainter().repaint();
		}
	}

	// event listeners
	class ActionHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent evt)
		{
			if(evt.getSource() == quicksearch)
			{
				String text = quicksearch.getText();
				if(text != null && text.length() != 0)
				{
					quicksearch.addCurrentToHistory();
					quicksearch.setText(null);
					SearchAndReplace.setSearchString(text);
					SearchAndReplace.find(View.this);
					textArea.requestFocus();
				}
				else
					new SearchDialog(View.this,null);
			}
		}
	}

	class CaretHandler implements CaretListener
	{
		public void caretUpdate(CaretEvent evt)
		{
			status.repaint();
		}
	}

	class StatusBar extends JComponent
	{
		String status;

		StatusBar()
		{
			StatusBar.this.setDoubleBuffered(true);
			StatusBar.this.setFont(UIManager.getFont("Label.font"));
			StatusBar.this.setForeground(UIManager.getColor("Label.foreground"));
			StatusBar.this.setBackground(UIManager.getColor("Label.background"));
		}

		void showStatus(String status)
		{
			StatusBar.this.status = status;
			StatusBar.this.repaint();
		}

		public void paint(Graphics g)
		{
			FontMetrics fm = g.getFontMetrics();

			int dot = textArea.getCaretPosition();

			int currLine = textArea.getCaretLine();
			int start = textArea.getLineStartOffset(currLine);
			int numLines = textArea.getLineCount();

			String status = (StatusBar.this.status != null
				? StatusBar.this.status
				: "col " + ((dot - start) + 1) + " line "
				+ (currLine + 1) + "/"
				+ numLines + " "
				+ (((currLine + 1) * 100) / numLines) + "%");

			g.drawString(status,0,(StatusBar.this.getHeight()
				+ fm.getAscent()) / 2);
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(200,0);
		}
	}
}

/*
 * ChangeLog:
 * $Log$
 * Revision 1.107  1999/11/21 01:20:30  sp
 * Bug fixes, EditBus updates, fixed some warnings generated by jikes +P
 *
 * Revision 1.106  1999/11/19 08:54:51  sp
 * EditBus integrated into the core, event system gone, bug fixes
 *
 * Revision 1.105  1999/11/12 09:06:01  sp
 * HTML bug fix
 *
 * Revision 1.104  1999/11/10 10:43:01  sp
 * Macros can now have shortcuts, various miscallaneous updates
 *
 * Revision 1.103  1999/11/09 10:14:34  sp
 * Macro code cleanups, menu item and tool bar clicks are recorded now, delete
 * word commands, check box menu item support
 *
 * Revision 1.102  1999/11/07 06:51:43  sp
 * Check box menu items supported
 *
 * Revision 1.101  1999/11/06 02:06:50  sp
 * Logging updates, bug fixing, icons, various other stuff
 *
 * Revision 1.100  1999/10/31 07:15:34  sp
 * New logging API, splash screen updates, bug fixes
 *
 * Revision 1.99  1999/10/28 09:07:21  sp
 * Directory list search
 *
 * Revision 1.98  1999/10/24 06:04:00  sp
 * QuickSearch in tool bar, auto indent updates, macro recorder updates
 *
 * Revision 1.97  1999/10/24 02:06:41  sp
 * Miscallaneous pre1 stuff
 *
 * Revision 1.96  1999/10/23 03:48:22  sp
 * Mode system overhaul, close all dialog box, misc other stuff
 *
 */
