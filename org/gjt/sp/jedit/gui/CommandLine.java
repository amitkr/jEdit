/*
 * CommandLine.java - Command line
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

import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.text.BadLocationException;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.util.Vector;
import org.gjt.sp.jedit.search.*;
import org.gjt.sp.jedit.*;
import org.gjt.sp.util.Log;

public class CommandLine extends JPanel
{
	public static final int NULL_STATE = 0;
	public static final int TOPLEVEL_STATE = 1;
	public static final int REPEAT_STATE = 2;
	public static final int PROMPT_ONE_CHAR_STATE = 3;
	public static final int PROMPT_LINE_STATE = 4;
	public static final int QUICK_SEARCH_STATE = 5;
	public static final int INCREMENTAL_SEARCH_STATE = 6;

	public CommandLine(View view)
	{
		super(new BorderLayout());

		this.view = view;

		add(BorderLayout.CENTER,textField = new CLITextField());
		textField.setFont(new Font("Dialog",Font.PLAIN,10));

		completionTimer = new Timer(0,new UpdateCompletions());
		completionTimer.setRepeats(false);

		Font font = new Font("Dialog",Font.BOLD,10);
		Insets margin = new Insets(0,0,0,0);
		ActionHandler actionHandler = new ActionHandler();
		ignoreCase = new JCheckBox(jEdit.getProperty("search.ignoreCase"));
		ignoreCase.setMnemonic(jEdit.getProperty("search.ignoreCase.mnemonic")
			.charAt(0));
		ignoreCase.setFont(font);
		ignoreCase.setMargin(margin);
		ignoreCase.setRequestFocusEnabled(false);
		ignoreCase.addActionListener(actionHandler);
		regexp = new JCheckBox(jEdit.getProperty("search.regexp"));
		regexp.setMnemonic(jEdit.getProperty("search.regexp.mnemonic")
			.charAt(0));
		regexp.setFont(font);
		regexp.setMargin(margin);
		regexp.setRequestFocusEnabled(false);
		regexp.addActionListener(actionHandler);

		searchSettings = new JPanel();
		searchSettings.setLayout(new BoxLayout(searchSettings,
			BoxLayout.X_AXIS));
		searchSettings.add(Box.createHorizontalStrut(6));
		searchSettings.add(ignoreCase);
		searchSettings.add(Box.createHorizontalStrut(6));
		searchSettings.add(regexp);

		updateSearchSettings();
	}

	public void setState(int state)
	{
		if(this.state == state)
			return;

		this.state = state;

		if(state == NULL_STATE)
		{
			view.showStatus(null);
			textField.setModel("cli");
			// must return focus to text area after a repeat
			view.getEditPane().focusOnTextArea();
		}
		else if(state == TOPLEVEL_STATE)
		{
			view.showStatus(jEdit.getProperty(
				"view.command-line.top-level"));
			textField.setModel("cli");
		}
		else if(state == REPEAT_STATE)
		{
			view.showStatus(jEdit.getProperty(
				"view.command-line.repeat"));
			textField.setModel(null);
		}
		else if(state == PROMPT_LINE_STATE)
			textField.setModel("cli.prompt");
		else if(state == QUICK_SEARCH_STATE)
		{
			view.showStatus(jEdit.getProperty(
				"view.status.quick-search"));
			textField.setModel("find");

			add(BorderLayout.EAST,searchSettings);

			Dimension dim = searchSettings.getPreferredSize();
			dim.height = textField.getHeight();
			searchSettings.setPreferredSize(dim);

			revalidate();

			textField.requestFocus();
		}
		else if(state == INCREMENTAL_SEARCH_STATE)
		{
			view.showStatus(jEdit.getProperty(
				"view.status.incremental-search"));
			textField.setModel("find");

			add(BorderLayout.EAST,searchSettings);

			Dimension dim = searchSettings.getPreferredSize();
			dim.height = textField.getHeight();
			searchSettings.setPreferredSize(dim);

			revalidate();

			textField.requestFocus();
		}

		reset();
	}

	public JTextField getTextField()
	{
		return textField;
	}

	/**
	 * Prompts the user to enter one character at the command line.
	 * @param prompt The prompt string
	 * @param promptAction This action will be executed with the
	 * character as the action command when the user enters it
	 * @since jEdit 2.6pre5
	 */
	public void promptOneChar(String prompt, EditAction promptAction)
	{
		view.showStatus(prompt);
		this.promptAction = promptAction;
		setState(PROMPT_ONE_CHAR_STATE);
		textField.requestFocus();
	}

	/**
	 * Prompts the user to enter one line of text at the command line.
	 * @param prompt The prompt string
	 * @param promptAction This action will be executed with the
	 * text as the action command when the user presses ENTER
	 * @since jEdit 2.6pre5
	 */
	public void promptLine(String prompt, EditAction promptAction)
	{
		view.showStatus(prompt);
		this.promptAction = promptAction;
		setState(PROMPT_LINE_STATE);
		textField.requestFocus();
	}

	public void updateSearchSettings()
	{
		ignoreCase.setSelected(SearchAndReplace.getIgnoreCase());
		regexp.setSelected(SearchAndReplace.getRegexp());
	}

	// private members
	private View view;
	private CLITextField textField;
	private CompletionWindow window;
	private EditAction[] actions;
	private Vector completions;
	private Timer completionTimer;

	private int state;

	private EditAction promptAction;

	private JPanel searchSettings;
	private JCheckBox ignoreCase, regexp;

	private int savedRepeatCount = 1;

	private void reset()
	{
		textField.setText(null);
		completionTimer.stop();
		hideCompletionWindow();
		remove(searchSettings);
		savedRepeatCount = view.getInputHandler().getRepeatCount();
	}

	private void setRepeatCount()
	{
		System.err.println(savedRepeatCount);
		view.getInputHandler().setRepeatCount(savedRepeatCount);
		savedRepeatCount = 1;
	}

	private void getCompletions(String text)
	{
		if(actions == null)
		{
			actions = jEdit.getActions();
			MiscUtilities.quicksort(actions,new ActionCompare());
			completions = new Vector(actions.length);
		}
		else
			completions.removeAllElements();

		for(int i = 0; i < actions.length; i++)
		{
			EditAction action = actions[i];
			String name = action.getName();
			if(!action.needsActionCommand() && name.startsWith(text))
				completions.addElement(name);
		}
	}

	private void updateCompletions()
	{
		if(window != null)
		{
			// if window is already visible, update them
			// immediately
			completionTimer.stop();
			completionTimer.setInitialDelay(0);
		}
		else
		{
			// don't show window if user is typing
			completionTimer.stop();
			completionTimer.setInitialDelay(300);
		}

		completionTimer.start();
	}

	private void updateCompletionsSafely()
	{
		if(state != TOPLEVEL_STATE)
			return;

		try
		{
			final String text = textField.getDocument().getText(0,
				textField.getSelectionStart());
			if(text.length() == 0)
			{
				hideCompletionWindow();
				return;
			}

			getCompletions(text);

			if(completions.size() == 0)
				hideCompletionWindow();
			else
				showCompletionWindow(completions);
		}
		catch(BadLocationException bl)
		{
			Log.log(Log.ERROR,this,bl);
		}
	}

	class ActionCompare implements MiscUtilities.Compare
	{
		public int compare(Object obj1, Object obj2)
		{
			EditAction a1 = (EditAction)obj1;
			EditAction a2 = (EditAction)obj2;
			return a1.getName().compareTo(a2.getName());
		}
	}

	class UpdateCompletions implements ActionListener
	{
		public void actionPerformed(ActionEvent evt)
		{
			updateCompletionsSafely();
		}
	}

	private void hideCompletionWindow()
	{
		if(window != null)
		{
			window.dispose();
			window = null;
		}
	}

	private void showCompletionWindow(Vector completions)
	{
		if(window != null)
		{
			window.setListData(completions);
			window.requestFocus();
			window.toFront();
		}
		else
			window = new CompletionWindow(completions);
	}

	private void executeAction(String actionName)
	{
		textField.addCurrentToHistory();

		getCompletions(actionName);
		EditAction action;
		if(completions.size() != 0)
		{
			actionName = (String)completions.elementAt(0);
			action = jEdit.getAction(actionName);
		}
		else
			action = null;

		if(action == null)
		{
			String[] args = { actionName };
			GUIUtilities.error(view,"unknown-action",args);
			view.getEditPane().focusOnTextArea();
		}
		else
		{
			setRepeatCount();
			view.getEditPane().focusOnTextArea();
			view.getInputHandler().executeAction(action,this,null);
		}
	}

	private void doQuickSearch()
	{
		String text = textField.getText();
		if(text.length() != 0)
		{
			textField.addCurrentToHistory();
			textField.setText(null);
			SearchAndReplace.setSearchFileSet(new CurrentBufferSet());
			SearchAndReplace.setSearchString(text);
			SearchAndReplace.find(view,view);
		}

		setState(NULL_STATE);
	}

	private void doIncrementalSearch(int start)
	{
		String text = textField.getText();
		if(text.length() != 0)
		{
			textField.addCurrentToHistory();
			SearchAndReplace.setSearchString(text);

			try
			{
				if(SearchAndReplace.find(view,view.getBuffer(),start))
					return;
			}
			catch(BadLocationException bl)
			{
				Log.log(Log.ERROR,this,bl);
			}
			catch(Exception ia)
			{
				// invalid regexp, ignore
			}

			getToolkit().beep();
		}
	}

	class ActionHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent evt)
		{
			if(evt.getSource() == ignoreCase)
			{
				SearchAndReplace.setIgnoreCase(ignoreCase
					.isSelected());
			}
			else if(evt.getSource() == regexp)
			{
				SearchAndReplace.setRegexp(regexp
					.isSelected());
			}
		}
	}

	class CLITextField extends HistoryTextField
	{
		CLITextField()
		{
			super("cli");
			CLITextField.this.addFocusListener(new FocusHandler());
			getDocument().addDocumentListener(new DocumentHandler());
		}

		protected void processKeyEvent(KeyEvent evt)
		{
			if(state == NULL_STATE)
				return;

			int modifiers = evt.getModifiers();

			switch(evt.getID())
			{
			case KeyEvent.KEY_TYPED:
				char ch = evt.getKeyChar();
				if(ch == KeyEvent.CHAR_UNDEFINED ||
					ch < 0x20 || ch == 0x7f
					|| (modifiers & KeyEvent.ALT_MASK) != 0)
					return;

				if(state == TOPLEVEL_STATE)
				{
					if(getText().length() == 0 && Character.isDigit(ch))
						handleDigit(evt);
					else if(ch == ' ')
						handleTopLevelEnter(evt);
					else
						super.processKeyEvent(evt);
				}
				else if(state == REPEAT_STATE)
				{
					if(Character.isDigit(ch))
						handleDigit(evt);
					else
					{
						setRepeatCount();
						setState(NULL_STATE);
						view.processKeyEvent(evt);
					}
				}
				else if(state == PROMPT_ONE_CHAR_STATE)
				{
					evt.consume();
					handlePromptOneChar(evt);
				}
				else if(state == PROMPT_LINE_STATE
					|| state == QUICK_SEARCH_STATE
					|| state == INCREMENTAL_SEARCH_STATE)
					super.processKeyEvent(evt);
				break;
			case KeyEvent.KEY_PRESSED:
				int keyCode = evt.getKeyCode();

				if(keyCode == KeyEvent.VK_CONTROL ||
					keyCode == KeyEvent.VK_SHIFT ||
					keyCode == KeyEvent.VK_ALT ||
					keyCode == KeyEvent.VK_META)
					return;

				if((modifiers & ~KeyEvent.SHIFT_MASK) == 0
					&& !evt.isActionKey()
					&& keyCode != KeyEvent.VK_BACK_SPACE
					&& keyCode != KeyEvent.VK_DELETE
					&& keyCode != KeyEvent.VK_ENTER
					&& keyCode != KeyEvent.VK_TAB
					&& keyCode != KeyEvent.VK_ESCAPE)
					return;
		
				if(modifiers == 0 && keyCode == KeyEvent.VK_ESCAPE)
				{
					setState(NULL_STATE);
					evt.consume();
					break;
				}
				else if(state == TOPLEVEL_STATE)
				{
					if(modifiers == 0 && keyCode == KeyEvent.VK_UP)
						handleTopLevelUp(evt);
					else if(modifiers == 0 && keyCode == KeyEvent.VK_DOWN)
						handleTopLevelDown(evt);
					else if(modifiers == 0 && keyCode == KeyEvent.VK_ENTER)
						handleTopLevelEnter(evt);
					else
						super.processKeyEvent(evt);
					break;
				}
				else if(state == PROMPT_ONE_CHAR_STATE)
				{
					if(modifiers == 0 &&
						(keyCode == KeyEvent.VK_ENTER
						|| keyCode == KeyEvent.VK_TAB))
					{
						evt.consume();
						handlePromptOneChar(evt);
					}
					break;
				}
				else if(state == PROMPT_LINE_STATE)
				{
					if(modifiers == 0 &&
						keyCode == KeyEvent.VK_ENTER)
					{
						evt.consume();
						handlePromptLine();
					}
					else
						super.processKeyEvent(evt);
					break;
				}
				else if(state == QUICK_SEARCH_STATE)
				{
					if(modifiers == 0 &&
						keyCode == KeyEvent.VK_ENTER)
					{
						evt.consume();
						doQuickSearch();
					}
					else
						super.processKeyEvent(evt);
					break;
				}
				else if(state == INCREMENTAL_SEARCH_STATE)
				{
					if(modifiers == 0 &&
						keyCode == KeyEvent.VK_ENTER)
					{
						evt.consume();
						doIncrementalSearch(view.getTextArea()
							.getSelectionEnd());
					}
					else
						super.processKeyEvent(evt);
					break;
				}
				else if(state == REPEAT_STATE)
				{
					// set state to NULL if the key was
					// handled
					setRepeatCount();
					view.getEditPane().focusOnTextArea();
					view.getInputHandler().keyPressed(evt);
					// ... but if the focus is returned
					// to the command field by this key
					// (eg, if the user presses C+ENTER
					// 10 C+ENTER) we *do not* change
					// state to NULL_STATE
					if(!textField.hasFocus() && evt.isConsumed())
						setState(NULL_STATE);
				}
			}
		}

		void handleDigit(KeyEvent evt)
		{
			InputHandler input = view.getInputHandler();
			savedRepeatCount *= 10;
			savedRepeatCount += (evt.getKeyChar() - '0');

			// in case we're in TOPLEVEL
			setState(REPEAT_STATE);

			// insert number into text field
			super.processKeyEvent(evt);
		}

		void handleTopLevelUp(KeyEvent evt)
		{
			if(window.isShowing())
			{
				int selected = window.list.getSelectedIndex();
				if(selected == 0)
					selected = window.list.getModel().getSize() - 1;
				else
					selected = selected - 1;

				window.list.setSelectedIndex(selected);
				window.list.ensureIndexIsVisible(selected);
	
				evt.consume();
			}
			else
				super.processKeyEvent(evt);
		}

		void handleTopLevelDown(KeyEvent evt)
		{
			if(window.isShowing())
			{
				int selected = window.list.getSelectedIndex();
				if(selected == window.list.getModel().getSize() - 1)
					selected = 0;
				else
					selected = selected + 1;

				window.list.setSelectedIndex(selected);
				window.list.ensureIndexIsVisible(selected);

				evt.consume();
			}
			else
				super.processKeyEvent(evt);
		}

		void handleTopLevelEnter(KeyEvent evt)
		{
			String action;
			if(window != null && window.isShowing())
				action = (String)window.list.getSelectedValue();
			else
				action = getText();
			if(action.length() != 0)
				executeAction(action);
			evt.consume();
		}

		void handlePromptOneChar(KeyEvent evt)
		{
			char ch = evt.getKeyChar();
			String arg = String.valueOf(ch);
			EditAction _promptAction = promptAction;
			setRepeatCount();
			setState(NULL_STATE);

			view.getInputHandler().executeAction(_promptAction,
				this,arg);
		}

		void handlePromptLine()
		{
			EditAction _promptAction = promptAction;
			String text = textField.getText();
			setRepeatCount();

			textField.addCurrentToHistory();
			setState(NULL_STATE);

			view.getInputHandler().executeAction(_promptAction,
				this,text);
		}

		class DocumentHandler implements DocumentListener
		{
			public void changedUpdate(DocumentEvent evt) {}

			public void insertUpdate(DocumentEvent evt)
			{
				if(state == TOPLEVEL_STATE)
					updateCompletions();
				else if(state == INCREMENTAL_SEARCH_STATE)
				{
					doIncrementalSearch(view.getTextArea()
						.getSelectionStart());
				}
			}

			public void removeUpdate(DocumentEvent evt)
			{
				if(state == TOPLEVEL_STATE)
					updateCompletions();
				else if(state == INCREMENTAL_SEARCH_STATE)
				{
					String text = textField.getText();
					if(text.length() != 0)
						doIncrementalSearch(0);
				}
			}
		}

		class FocusHandler implements FocusListener
		{
			public void focusGained(FocusEvent evt)
			{
				if(state == NULL_STATE)
					setState(TOPLEVEL_STATE);
			}

			public void focusLost(FocusEvent evt)
			{
				if(state == TOPLEVEL_STATE)
					setState(NULL_STATE);
			}
		}
	}

	class CompletionWindow extends JWindow
	{
		JList list;

		CompletionWindow(Vector items)
		{
			super(view);

			list = new JList();
			list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			list.addMouseListener(new MouseHandler());
			getContentPane().add(BorderLayout.CENTER,new JScrollPane(list));

			setListData(items);

			CompletionWindow.this.show();
		}

		void setListData(Vector items)
		{
			list.setListData(items);
			list.setVisibleRowCount(Math.min(8,items.size()));
			list.setSelectedIndex(0);

			pack();
			Point loc = new Point(0,-CompletionWindow.this.getSize().height);
			SwingUtilities.convertPointToScreen(loc,textField);
			CompletionWindow.this.setLocation(loc);
		}

		class MouseHandler extends MouseAdapter
		{
			public void mouseClicked(MouseEvent evt)
			{
				executeAction((String)list.getSelectedValue());
			}
		}
	}
}

/*
 * Change Log:
 * $Log$
 * Revision 1.3  2000/09/06 04:39:47  sp
 * bug fixes
 *
 * Revision 1.2  2000/09/03 03:16:53  sp
 * Search bar integrated with command line, enhancements throughout
 *
 * Revision 1.1  2000/09/01 11:31:00  sp
 * Rudimentary 'command line', similar to emacs minibuf
 *
 */
