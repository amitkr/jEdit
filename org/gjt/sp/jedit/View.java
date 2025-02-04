/*
 * View.java - jEdit view
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 1998, 1999, 2000, 2001 Slava Pestov
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
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import org.gjt.sp.jedit.msg.*;
import org.gjt.sp.jedit.gui.*;
import org.gjt.sp.jedit.search.*;
import org.gjt.sp.jedit.textarea.*;
import org.gjt.sp.util.Log;
//}}}

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
	//{{{ User interface

	//{{{ ToolBar-related constants

	//{{{ Groups
	/**
	 * The group of tool bars above the DockableWindowManager
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int TOP_GROUP = 0;

	/**
	 * The group of tool bars below the DockableWindowManager
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int BOTTOM_GROUP = 1;
	public static final int DEFAULT_GROUP = TOP_GROUP;
	//}}}

	//{{{ Layers

	// Common layers
	/**
	 * The highest possible layer.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int TOP_LAYER = Integer.MAX_VALUE;

	/**
	 * The default layer for tool bars with no preference.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int DEFAULT_LAYER = 0;

	/**
	 * The lowest possible layer.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int BOTTOM_LAYER = Integer.MIN_VALUE;

	// Layers for top group
	/**
	 * Above system tool bar layer.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int ABOVE_SYSTEM_BAR_LAYER = 150;

	/**
	 * System tool bar layer. jEdit uses this for the main tool bar.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int SYSTEM_BAR_LAYER = 100;

	/**
	 * Search bar layer. jEdit uses this for the search bar.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int SEARCH_BAR_LAYER = 75;

	/**
	 * Below search bar layer. This is above the default layer for the
	 * top group.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int BELOW_SEARCH_BAR_LAYER = 50;

	// Layers for bottom group
	/**
	 * Above status bar layer. This is above the status bar and below the
	 * default layer for the bottom group. Vimulator will use this.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int ABOVE_STATUS_BAR_LAYER = -50;

	/**
	 * Status bar layer. jEdit uses this for the status bar.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int STATUS_BAR_LAYER = -100;

	/**
	 * Below status bar layer. BufferSelector will use this.
	 * @see #addToolBar(int,int,java.awt.Component)
	 * @since jEdit 4.0pre7
	 */
	public static final int BELOW_STATUS_BAR_LAYER = -150;
	//}}}

	//}}}

	//{{{ getDockableWindowManager() method
	/**
	 * Returns the dockable window manager associated with this view.
	 * @since jEdit 2.6pre3
	 */
	public DockableWindowManager getDockableWindowManager()
	{
		return dockableWindowManager;
	} //}}}

	//{{{ getToolBar() method
	/**
	 * Returns the view's tool bar.
	 * @since jEdit 3.2.1
	 */
	public JToolBar getToolBar()
	{
		return toolBar;
	} //}}}

	//{{{ addToolBar() method
	/**
	 * Adds a tool bar to this view.
	 * @param toolBar The tool bar
	 */
	public void addToolBar(Component toolBar)
	{
		addToolBar(DEFAULT_GROUP, DEFAULT_LAYER, toolBar);
	} //}}}

	//{{{ addToolBar() method
	/**
	 * Adds a tool bar to this view.
	 * @param group The tool bar group to add to
	 * @param toolBar The tool bar
	 * @see org.gjt.sp.jedit.gui.ToolBarManager
	 * @since jEdit 4.0pre7
	 */
	public void addToolBar(int group, Component toolBar)
	{
		addToolBar(group, DEFAULT_LAYER, toolBar);
	} //}}}

	//{{{ addToolBar() method
	/**
	 * Adds a tool bar to this view.
	 * @param group The tool bar group to add to
	 * @param layer The layer of the group to add to
	 * @param toolBar The tool bar
	 * @see org.gjt.sp.jedit.gui.ToolBarManager
	 * @since jEdit 4.0pre7
	 */
	public void addToolBar(int group, int layer, Component toolBar)
	{
		toolBarManager.addToolBar(group, layer, toolBar);
		getRootPane().revalidate();
	} //}}}

	//{{{ removeToolBar() method
	/**
	 * Removes a tool bar from this view.
	 * @param toolBar The tool bar
	 */
	public void removeToolBar(Component toolBar)
	{
		toolBarManager.removeToolBar(toolBar);
		getRootPane().revalidate();
	} //}}}

	//{{{ showWaitCursor() method
	/**
	 * Shows the wait cursor and glass pane.
	 */
	public synchronized void showWaitCursor()
	{
		if(waitCount++ == 0)
		{
			Cursor cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
			setCursor(cursor);
			EditPane[] editPanes = getEditPanes();
			for(int i = 0; i < editPanes.length; i++)
			{
				EditPane editPane = editPanes[i];
				editPane.getTextArea().getPainter()
					.setCursor(cursor);
			}
		}
	} //}}}

	//{{{ hideWaitCursor() method
	/**
	 * Hides the wait cursor and glass pane.
	 */
	public synchronized void hideWaitCursor()
	{
		if(waitCount > 0)
			waitCount--;

		if(waitCount == 0)
		{
			// still needed even though glass pane
			// has a wait cursor
			Cursor cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
			setCursor(cursor);
			cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
			EditPane[] editPanes = getEditPanes();
			for(int i = 0; i < editPanes.length; i++)
			{
				EditPane editPane = editPanes[i];
				editPane.getTextArea().getPainter()
					.setCursor(cursor);
			}
		}
	} //}}}

	//{{{ getSearchBar() method
	/**
	 * Returns the search bar.
	 * @since jEdit 2.4pre4
	 */
	public final SearchBar getSearchBar()
	{
		return searchBar;
	} //}}}

	//{{{ getStatus() method
	/**
	 * Returns the status bar.
	 * @since jEdit 3.2pre2
	 */
	public StatusBar getStatus()
	{
		return status;
	} //}}}

	//}}}

	//{{{ Input handling

	//{{{ getKeyEventInterceptor() method
	/**
	 * Returns the listener that will handle all key events in this
	 * view, if any.
	 */
	public KeyListener getKeyEventInterceptor()
	{
		return keyEventInterceptor;
	} //}}}

	//{{{ setKeyEventInterceptor() method
	/**
	 * Sets the listener that will handle all key events in this
	 * view. For example, the complete word command uses this so
	 * that all key events are passed to the word list popup while
	 * it is visible.
	 * @param comp The component
	 */
	public void setKeyEventInterceptor(KeyListener listener)
	{
		this.keyEventInterceptor = listener;
	} //}}}

	//{{{ getInputHandler() method
	/**
	 * Returns the input handler.
	 */
	public InputHandler getInputHandler()
	{
		return inputHandler;
	} //}}}

	//{{{ setInputHandler() method
	/**
	 * Sets the input handler.
	 * @param inputHandler The new input handler
	 */
	public void setInputHandler(InputHandler inputHandler)
	{
		this.inputHandler = inputHandler;
	} //}}}

	//{{{ getMacroRecorder() method
	/**
	 * Returns the macro recorder.
	 */
	public Macros.Recorder getMacroRecorder()
	{
		return recorder;
	} //}}}

	//{{{ setMacroRecorder() method
	/**
	 * Sets the macro recorder.
	 * @param recorder The macro recorder
	 */
	public void setMacroRecorder(Macros.Recorder recorder)
	{
		this.recorder = recorder;
	} //}}}

	//{{{ processKeyEvent() method
	/**
	 * Forwards key events directly to the input handler.
	 * This is slightly faster than using a KeyListener
	 * because some Swing overhead is avoided.
	 */
	public void processKeyEvent(KeyEvent evt)
	{
		if(isClosed())
			return;

		if(getFocusOwner() instanceof JComponent)
		{
			JComponent comp = (JComponent)getFocusOwner();
			InputMap map = comp.getInputMap();
			ActionMap am = comp.getActionMap();

			if(map != null && am != null && comp.isEnabled())
			{
				Object binding = map.get(KeyStroke.getKeyStrokeForEvent(evt));
				if(binding != null && am.get(binding) != null)
				{
					return;
				}
			}
		}

		if(getFocusOwner() instanceof JTextComponent)
		{
			// fix for the bug where key events in JTextComponents
			// inside views are also handled by the input handler
			if(evt.getID() == KeyEvent.KEY_PRESSED)
			{
				switch(evt.getKeyCode())
				{
				case KeyEvent.VK_BACK_SPACE:
				case KeyEvent.VK_TAB:
				case KeyEvent.VK_ENTER:
					return;
				}
			}

			Keymap keymap = ((JTextComponent)getFocusOwner())
				.getKeymap();
			if(keymap.getAction(KeyStroke.getKeyStrokeForEvent(evt)) != null)
				return;
		}

		if(evt.isConsumed())
			return;

		evt = KeyEventWorkaround.processKeyEvent(evt);
		if(evt == null)
			return;

		switch(evt.getID())
		{
		case KeyEvent.KEY_TYPED:
			// Handled in text area
			if(keyEventInterceptor != null)
				/* keyEventInterceptor.keyTyped(evt) */;
			else if(inputHandler.isPrefixActive()
				&& !getTextArea().hasFocus())
				inputHandler.keyTyped(evt);
			break;
		case KeyEvent.KEY_PRESSED:
			if(keyEventInterceptor != null)
				keyEventInterceptor.keyPressed(evt);
			else
				inputHandler.keyPressed(evt);
			break;
		case KeyEvent.KEY_RELEASED:
			if(keyEventInterceptor != null)
				keyEventInterceptor.keyReleased(evt);
			else
				inputHandler.keyReleased(evt);
			break;
		}

		if(!evt.isConsumed())
			super.processKeyEvent(evt);
	} //}}}

	//}}}

	//{{{ Buffers, edit panes, split panes

	//{{{ splitHorizontally() method
	/**
	 * Splits the view horizontally.
	 * @since jEdit 2.7pre2
	 */
	public void splitHorizontally()
	{
		split(JSplitPane.VERTICAL_SPLIT);
	} //}}}

	//{{{ splitVertically() method
	/**
	 * Splits the view vertically.
	 * @since jEdit 2.7pre2
	 */
	public void splitVertically()
	{
		split(JSplitPane.HORIZONTAL_SPLIT);
	} //}}}

	//{{{ split() method
	/**
	 * Splits the view.
	 * @since jEdit 2.3pre2
	 */
	public void split(int orientation)
	{
		editPane.saveCaretInfo();
		EditPane oldEditPane = editPane;
		setEditPane(createEditPane(oldEditPane.getBuffer()));
		editPane.loadCaretInfo();

		JComponent oldParent = (JComponent)oldEditPane.getParent();

		if(oldParent instanceof JSplitPane)
		{
			JSplitPane oldSplitPane = (JSplitPane)oldParent;
			int dividerPos = oldSplitPane.getDividerLocation();

			Component left = oldSplitPane.getLeftComponent();
			final JSplitPane newSplitPane = new JSplitPane(orientation,
				oldEditPane,editPane);

			if(left == oldEditPane)
				oldSplitPane.setLeftComponent(newSplitPane);
			else
				oldSplitPane.setRightComponent(newSplitPane);

			oldSplitPane.setDividerLocation(dividerPos);

			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					newSplitPane.setDividerLocation(0.5);
					editPane.focusOnTextArea();
				}
			});
		}
		else
		{
			JSplitPane newSplitPane = splitPane = new JSplitPane(orientation,
				oldEditPane,editPane);
			newSplitPane.setBorder(null);
			oldParent.add(splitPane);
			oldParent.revalidate();

			Dimension size;
			if(oldParent instanceof JSplitPane)
				size = oldParent.getSize();
			else
				size = oldEditPane.getSize();
			newSplitPane.setDividerLocation(((orientation
				== JSplitPane.VERTICAL_SPLIT) ? size.height
				: size.width) / 2);
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					editPane.focusOnTextArea();
				}
			});
		}
	} //}}}

	//{{{ unsplit() method
	/**
	 * Unsplits the view.
	 * @since jEdit 2.3pre2
	 */
	public void unsplit()
	{
		if(splitPane != null)
		{
			EditPane[] editPanes = getEditPanes();
			for(int i = 0; i < editPanes.length; i++)
			{
				EditPane _editPane = editPanes[i];
				if(editPane != _editPane)
					_editPane.close();
			}

			JComponent parent = (JComponent)splitPane.getParent();

			parent.remove(splitPane);
			parent.add(editPane);
			parent.revalidate();

			splitPane = null;
			updateTitle();
		}

		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				editPane.focusOnTextArea();
			}
		});
	} //}}}

	//{{{ nextTextArea() method
	/**
	 * Moves keyboard focus to the next text area.
	 * @since jEdit 2.7pre4
	 */
	public void nextTextArea()
	{
		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
		{
			if(editPane == editPanes[i])
			{
				if(i == editPanes.length - 1)
					editPanes[0].focusOnTextArea();
				else
					editPanes[i+1].focusOnTextArea();
				break;
			}
		}
	} //}}}

	//{{{ prevTextArea() method
	/**
	 * Moves keyboard focus to the previous text area.
	 * @since jEdit 2.7pre4
	 */
	public void prevTextArea()
	{
		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
		{
			if(editPane == editPanes[i])
			{
				if(i == 0)
					editPanes[editPanes.length - 1].focusOnTextArea();
				else
					editPanes[i-1].focusOnTextArea();
				break;
			}
		}
	} //}}}

	//{{{ getSplitPane() method
	/**
	 * Returns the top-level split pane, if any.
	 * @since jEdit 2.3pre2
	 */
	public JSplitPane getSplitPane()
	{
		return splitPane;
	} //}}}

	//{{{ getBuffer() method
	/**
	 * Returns the current edit pane's buffer.
	 */
	public Buffer getBuffer()
	{
		return editPane.getBuffer();
	} //}}}

	//{{{ setBuffer() method
	/**
	 * Sets the current edit pane's buffer.
	 */
	public void setBuffer(Buffer buffer)
	{
		editPane.setBuffer(buffer);
	} //}}}

	//{{{ getTextArea() method
	/**
	 * Returns the current edit pane's text area.
	 */
	public JEditTextArea getTextArea()
	{
		if(editPane == null)
			return null;
		else
			return editPane.getTextArea();
	} //}}}

	//{{{ getEditPane() method
	/**
	 * Returns the current edit pane.
	 * @since jEdit 2.5pre2
	 */
	public EditPane getEditPane()
	{
		return editPane;
	} //}}}

	//{{{ getEditPanes() method
	/**
	 * Returns all edit panes.
	 * @since jEdit 2.5pre2
	 */
	public EditPane[] getEditPanes()
	{
		if(splitPane == null)
		{
			EditPane[] ep = { editPane };
			return ep;
		}
		else
		{
			Vector vec = new Vector();
			getEditPanes(vec,splitPane);
			EditPane[] ep = new EditPane[vec.size()];
			vec.copyInto(ep);
			return ep;
		}
	} //}}}

	//{{{ getSplitConfig() method
	/**
	 * Returns a string that can be passed to the view constructor to
	 * recreate the current split configuration in a new view.
	 * @since jEdit 3.2pre2
	 */
	public String getSplitConfig()
	{
		// this code isn't finished yet

		StringBuffer splitConfig = new StringBuffer();
		//if(splitPane != null)
		//	getSplitConfig(splitPane,splitConfig);
		//else
			splitConfig.append(getBuffer().getPath());
		return splitConfig.toString();
	} //}}}

	//{{{ updateGutterBorders() method
	/**
	 * Updates the borders of all gutters in this view to reflect the
	 * currently focused text area.
	 * @since jEdit 2.6final
	 */
	public void updateGutterBorders()
	{
		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
			editPanes[i].getTextArea().getGutter().updateBorder();
	} //}}}

	//}}}

	//{{{ Synchronized scrolling

	//{{{ isSynchroScrollEnabled() method
	/**
	 * Returns if synchronized scrolling is enabled.
	 * @since jEdit 2.7pre1
	 */
	public boolean isSynchroScrollEnabled()
	{
		return synchroScroll;
	} //}}}

	//{{{ toggleSynchroScrollEnabled() method
	/**
	 * Toggles synchronized scrolling.
	 * @since jEdit 2.7pre2
	 */
	public void toggleSynchroScrollEnabled()
	{
		setSynchroScrollEnabled(!synchroScroll);
	} //}}}

	//{{{ setSynchroScrollEnabled() method
	/**
	 * Sets synchronized scrolling.
	 * @since jEdit 2.7pre1
	 */
	public void setSynchroScrollEnabled(boolean synchroScroll)
	{
		this.synchroScroll = synchroScroll;
		JEditTextArea textArea = getTextArea();
		int firstLine = textArea.getFirstLine();
		int horizontalOffset = textArea.getHorizontalOffset();
		synchroScrollVertical(textArea,firstLine);
		synchroScrollHorizontal(textArea,horizontalOffset);
	} //}}}

	//{{{ synchroScrollVertical() method
	/**
	 * Sets the first line of all text areas.
	 * @param textArea The text area that is propagating this change
	 * @param firstLine The first line
	 * @since jEdit 2.7pre1
	 */
	public void synchroScrollVertical(JEditTextArea textArea, int firstLine)
	{
		if(!synchroScroll)
			return;

		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
		{
			if(editPanes[i].getTextArea() != textArea)
				editPanes[i].getTextArea()._setFirstLine(firstLine);
		}
	} //}}}

	//{{{ synchroScrollHorizontal() method
	/**
	 * Sets the horizontal offset of all text areas.
	 * @param textArea The text area that is propagating this change
	 * @param horizontalOffset The horizontal offset
	 * @since jEdit 2.7pre1
	 */
	public void synchroScrollHorizontal(JEditTextArea textArea, int horizontalOffset)
	{
		if(!synchroScroll)
			return;

		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
		{
			if(editPanes[i].getTextArea() != textArea)
				editPanes[i].getTextArea()._setHorizontalOffset(horizontalOffset);
		}
	} //}}}

	//}}}

	//{{{ quickIncrementalSearch() method
	/**
	 * Quick search.
	 * @since jEdit 4.0pre3
	 */
	public void quickIncrementalSearch(boolean word)
	{
		if(searchBar == null)
		{
			getToolkit().beep();
			return;
		}

		JEditTextArea textArea = getTextArea();

		String text = textArea.getSelectedText();
		if(text == null && word)
		{
			textArea.selectWord();
			text = textArea.getSelectedText();
		}
		else if(text != null && text.indexOf('\n') != -1)
			text = null;

		searchBar.setHyperSearch(false);
		searchBar.getField().setText(text);
		searchBar.getField().requestFocus();
		searchBar.getField().selectAll();
	} //}}}

	//{{{ quickHyperSearch() method
	/**
	 * Quick HyperSearch.
	 * @since jEdit 4.0pre3
	 */
	public void quickHyperSearch(boolean word)
	{
		JEditTextArea textArea = getTextArea();

		String text = textArea.getSelectedText();
		if(text == null && word)
		{
			textArea.selectWord();
			text = textArea.getSelectedText();
		}

		if(text != null && text.indexOf('\n') == -1)
		{
			HistoryModel.getModel("find").addItem(text);
			SearchAndReplace.setSearchString(text);
			SearchAndReplace.setSearchFileSet(new CurrentBufferSet());
			SearchAndReplace.hyperSearch(this);
		}
		else
		{
			if(searchBar == null)
			{
				getToolkit().beep();
				return;
			}

			searchBar.setHyperSearch(true);
			searchBar.getField().setText(null);
			searchBar.getField().requestFocus();
			searchBar.getField().selectAll();
		}
	} //}}}

	//{{{ isClosed() method
	/**
	 * Returns true if this view has been closed with
	 * <code>jEdit.closeView()</code>.
	 */
	public boolean isClosed()
	{
		return closed;
	} //}}}

	//{{{ getNext() method
	/**
	 * Returns the next view in the list.
	 */
	public View getNext()
	{
		return next;
	} //}}}

	//{{{ getPrev() method
	/**
	 * Returns the previous view in the list.
	 */
	public View getPrev()
	{
		return prev;
	} //}}}

	//{{{ handleMessage() method
	public void handleMessage(EBMessage msg)
	{
		if(msg instanceof PropertiesChanged)
			propertiesChanged();
		else if(msg instanceof SearchSettingsChanged)
		{
			if(searchBar != null)
				searchBar.update();
		}
		else if(msg instanceof BufferUpdate)
			handleBufferUpdate((BufferUpdate)msg);
		else if(msg instanceof EditPaneUpdate)
			handleEditPaneUpdate((EditPaneUpdate)msg);
		else if(msg instanceof MultiSelectStatusChanged)
			status.updateMiscStatus();
	} //}}}

	//{{{ Package-private members
	View prev;
	View next;

	//{{{ View constructor
	View(Buffer buffer, String splitConfig)
	{
		enableEvents(AWTEvent.KEY_EVENT_MASK);

		setIconImage(GUIUtilities.getEditorIcon());

		dockableWindowManager = new DockableWindowManager(this);

		JPanel topToolBars = new JPanel(new VariableGridLayout(
			VariableGridLayout.FIXED_NUM_COLUMNS,
			1));
		JPanel bottomToolBars = new JPanel(new VariableGridLayout(
			VariableGridLayout.FIXED_NUM_COLUMNS,
			1));

		toolBarManager = new ToolBarManager(topToolBars, bottomToolBars);

		getContentPane().add(BorderLayout.NORTH,topToolBars);
		getContentPane().add(BorderLayout.CENTER,dockableWindowManager);
		getContentPane().add(BorderLayout.SOUTH,bottomToolBars);

		status = new StatusBar(this);
		addToolBar(BOTTOM_GROUP, STATUS_BAR_LAYER, status);

		setJMenuBar(GUIUtilities.loadMenuBar("view.mbar"));

		inputHandler = new DefaultInputHandler(this,(DefaultInputHandler)
			jEdit.getInputHandler());

		Component comp = restoreSplitConfig(buffer,splitConfig);
		dockableWindowManager.add(comp);

		status.updateBufferStatus();
		status.updateMiscStatus();

		EditBus.addToBus(this);

		propertiesChanged();

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowHandler());

		dockableWindowManager.init();
	} //}}}

	//{{{ close() method
	void close()
	{
		closed = true;

		// save dockable window geometry, and close 'em
		dockableWindowManager.close();

		GUIUtilities.saveGeometry(this,"view");
		EditBus.removeFromBus(this);
		dispose();

		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
			editPanes[i].close();

		// null some variables so that retaining references
		// to closed views won't hurt as much.
		toolBarManager = null;
		toolBar = null;
		searchBar = null;
		splitPane = null;
		inputHandler = null;
		recorder = null;

		setContentPane(new JPanel());
	} //}}}

	//{{{ updateTitle() method
	/**
	 * Updates the title bar.
	 */
	void updateTitle()
	{
		Vector buffers = new Vector();
		EditPane[] editPanes = getEditPanes();
		for(int i = 0; i < editPanes.length; i++)
		{
			Buffer buffer = editPanes[i].getBuffer();
			if(buffers.indexOf(buffer) == -1)
				buffers.addElement(buffer);
		}

		StringBuffer title = new StringBuffer(jEdit.getProperty("view.title"));
		for(int i = 0; i < buffers.size(); i++)
		{
			if(i != 0)
				title.append(", ");

			Buffer buffer = (Buffer)buffers.elementAt(i);
			title.append((showFullPath && !buffer.isNewFile())
				? buffer.getPath() : buffer.getName());
		}
		setTitle(title.toString());
	} //}}}

	//}}}

	//{{{ Private members

	//{{{ Instance variables
	private boolean closed;

	private DockableWindowManager dockableWindowManager;

	private ToolBarManager toolBarManager;

	private JToolBar toolBar;
	private SearchBar searchBar;

	private boolean synchroScroll;

	private EditPane editPane;
	private JSplitPane splitPane;

	private StatusBar status;

	private KeyListener keyEventInterceptor;
	private InputHandler inputHandler;
	private Macros.Recorder recorder;

	private int waitCount;

	private boolean showFullPath;
	//}}}

	//{{{ getEditPanes() method
	private void getEditPanes(Vector vec, Component comp)
	{
		if(comp instanceof EditPane)
			vec.addElement(comp);
		else if(comp instanceof JSplitPane)
		{
			JSplitPane split = (JSplitPane)comp;
			getEditPanes(vec,split.getLeftComponent());
			getEditPanes(vec,split.getRightComponent());
		}
	} //}}}

	//{{{ getSplitConfig() method
	/*
	 * The split config is recorded in a simple RPN "language":
	 * "vertical" pops the two topmost elements off the stack, creates a
	 * vertical split
	 * "horizontal" pops the two topmost elements off the stack, creates a
	 * horizontal split
	 * A path name creates an edit pane editing that buffer
	 */
	private void getSplitConfig(JSplitPane splitPane,
		StringBuffer splitConfig)
	{
		Component left = splitPane.getLeftComponent();
		if(left instanceof JSplitPane)
			getSplitConfig((JSplitPane)left,splitConfig);
		else
		{
			splitConfig.append('\t');
			splitConfig.append(((EditPane)left).getBuffer().getPath());
		}

		Component right = splitPane.getRightComponent();
		if(right instanceof JSplitPane)
			getSplitConfig((JSplitPane)right,splitConfig);
		else
		{
			splitConfig.append('\t');
			splitConfig.append(((EditPane)right).getBuffer().getPath());
		}

		splitConfig.append(splitPane.getOrientation()
			== JSplitPane.VERTICAL_SPLIT ? "\tvertical" : "\thorizontal");
	} //}}}

	//{{{ restoreSplitConfig() method
	private Component restoreSplitConfig(Buffer buffer, String splitConfig)
	{
		if(buffer != null)
			return (editPane = createEditPane(buffer));
		else if(splitConfig == null)
			return (editPane = createEditPane(jEdit.getFirstBuffer()));

		Stack stack = new Stack();

		StringTokenizer st = new StringTokenizer(splitConfig,"\t");

		while(st.hasMoreTokens())
		{
			String token = st.nextToken();
			if(token.equals("vertical"))
			{
				stack.push(splitPane = new JSplitPane(
					JSplitPane.VERTICAL_SPLIT,
					(Component)stack.pop(),
					(Component)stack.pop()));
				splitPane.setBorder(null);
				splitPane.setDividerLocation(0.5);
			}
			else if(token.equals("horizontal"))
			{
				stack.push(splitPane = new JSplitPane(
					JSplitPane.HORIZONTAL_SPLIT,
					(Component)stack.pop(),
					(Component)stack.pop()));
				splitPane.setBorder(null);
				splitPane.setDividerLocation(0.5);
			}
			else
			{
				buffer = jEdit.getBuffer(token);
				if(buffer == null)
					buffer = jEdit.getFirstBuffer();

				stack.push(editPane = createEditPane(buffer));
			}
		}

		return (Component)stack.peek();
	} //}}}

	//{{{ propertiesChanged() method
	/**
	 * Reloads various settings from the properties.
	 */
	private void propertiesChanged()
	{
		loadToolBars();

		showFullPath = jEdit.getBooleanProperty("view.showFullPath");
		updateTitle();

		dockableWindowManager.propertiesChanged();

		SwingUtilities.updateComponentTreeUI(getRootPane());
	} //}}}

	//{{{ loadToolBars() method
	private void loadToolBars()
	{
		if(jEdit.getBooleanProperty("view.showToolbar"))
		{
			if(toolBar != null)
				toolBarManager.removeToolBar(toolBar);

			toolBar = GUIUtilities.loadToolBar("view.toolbar");

			addToolBar(TOP_GROUP, SYSTEM_BAR_LAYER, toolBar);
		}
		else if(toolBar != null)
		{
			removeToolBar(toolBar);
			toolBar = null;
		}

		if(jEdit.getBooleanProperty("view.showSearchbar"))
		{
			if(searchBar == null)
			{
				searchBar = new SearchBar(this);
				addToolBar(TOP_GROUP, SEARCH_BAR_LAYER, searchBar);
			}
		}
		else if(searchBar != null)
		{
			removeToolBar(searchBar);
			searchBar = null;
		}
	} //}}}

	//{{{ createEditPane() method
	private EditPane createEditPane(Buffer buffer)
	{
		EditPane editPane = new EditPane(this,buffer);
		JEditTextArea textArea = editPane.getTextArea();
		textArea.addFocusListener(new FocusHandler());
		textArea.addCaretListener(new CaretHandler());
		textArea.addScrollListener(new ScrollHandler());
		EditBus.send(new EditPaneUpdate(editPane,EditPaneUpdate.CREATED));
		return editPane;
	} //}}}

	//{{{ setEditPane() method
	private void setEditPane(EditPane editPane)
	{
		this.editPane = editPane;
		status.repaintCaretStatus();
		status.updateBufferStatus();
		status.updateMiscStatus();
	} //}}}

	//{{{ handleBufferUpdate() method
	private void handleBufferUpdate(BufferUpdate msg)
	{
		Buffer buffer = msg.getBuffer();
		if(msg.getWhat() == BufferUpdate.DIRTY_CHANGED)
		{
			if(!buffer.isDirty())
			{
				// have to update title after each save
				// in case it was a 'save as'
				EditPane[] editPanes = getEditPanes();
				for(int i = 0; i < editPanes.length; i++)
				{
					if(editPanes[i].getBuffer() == buffer)
					{
						updateTitle();
						break;
					}
				}
			}
		}
	} //}}}

	//{{{ handleEditPaneUpdate() method
	private void handleEditPaneUpdate(EditPaneUpdate msg)
	{
		if(msg.getEditPane().getView() == this
			&& msg.getWhat() == EditPaneUpdate.BUFFER_CHANGED)
		{
			status.repaintCaretStatus();
			status.updateBufferStatus();
			status.updateMiscStatus();
		}
	} //}}}

	//}}}

	//{{{ Inner classes

	//{{{ CaretHandler class
	class CaretHandler implements CaretListener
	{
		public void caretUpdate(CaretEvent evt)
		{
			status.repaintCaretStatus();
			status.updateMiscStatus();
		}
	} //}}}

	//{{{ FocusHandler class
	class FocusHandler extends FocusAdapter
	{
		public void focusGained(FocusEvent evt)
		{
			// walk up hierarchy, looking for an EditPane
			Component comp = (Component)evt.getSource();
			while(!(comp instanceof EditPane))
			{
				if(comp == null)
					return;

				comp = comp.getParent();
			}

			setEditPane((EditPane)comp);
		}
	} //}}}

	//{{{ ScrollHandler class
	class ScrollHandler implements ScrollListener
	{
		public void scrolledVertically(JEditTextArea textArea)
		{
			if(getTextArea() == textArea)
				status.repaintCaretStatus();
		}

		public void scrolledHorizontally(JEditTextArea textArea) {}
	} //}}}

	//{{{ WindowHandler class
	class WindowHandler extends WindowAdapter
	{
		public void windowActivated(WindowEvent evt)
		{
			final Vector buffers = new Vector();
			EditPane[] editPanes = getEditPanes();
			for(int i = 0; i < editPanes.length; i++)
			{
				Buffer buffer = ((EditPane)editPanes[i])
					.getBuffer();
				if(buffers.contains(buffer))
					continue;
				else
					buffers.addElement(buffer);
			}

			// People have reported hangs with JDK 1.4; might be
			// caused by modal dialogs being displayed from
			// windowActivated()
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					for(int i = 0; i < buffers.size(); i++)
					{
						((Buffer)buffers.elementAt(i))
							.checkModTime(View.this);
					}
				}
			});
		}

		public void windowClosing(WindowEvent evt)
		{
			jEdit.closeView(View.this);
		}
	} //}}}

	//}}}
}
