/*
 * SearchDialog.java - Search and replace dialog
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

package org.gjt.sp.jedit.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import org.gjt.sp.jedit.search.*;
import org.gjt.sp.jedit.*;

/**
 * Search and replace dialog.
 * @author Slava Pestov
 * @version $Id$
 */
public class SearchDialog extends JDialog
{
	public SearchDialog(View view, String defaultFind)
	{
		super(view,jEdit.getProperty("search.title"),false);
		this.view = view;

		fileset = SearchAndReplace.getSearchFileSet();

		find = new HistoryTextField("find");
		find.setText(defaultFind);

		replace = new HistoryTextField("replace");
		keepDialog = new JCheckBox(jEdit.getProperty(
			"search.keepDialog"),"on".equals(jEdit.getProperty(
			"search.keepDialog.toggle")));
		ignoreCase = new JCheckBox(jEdit.getProperty(
			"search.ignoreCase"),SearchAndReplace.getIgnoreCase());
		regexp = new JCheckBox(jEdit.getProperty(
			"search.regexp"),SearchAndReplace.getRegexp());
		multifile = new JCheckBox();
		multifile.getModel().setSelected(!(fileset
			instanceof CurrentBufferSet));
		multifileBtn = new JButton(jEdit.getProperty(
			"search.multifile"));
		findBtn = new JButton(jEdit.getProperty("search.findBtn"));
		replaceSelection = new JButton(jEdit.getProperty("search"
			+ ".replaceSelection"));
		replaceSelection.setMnemonic(jEdit.getProperty("search"
			+ ".replaceSelection.mnemonic").charAt(0));
		replaceAll = new JButton(jEdit.getProperty("search.replaceAll"));
		replaceAll.setMnemonic(jEdit.getProperty("search.replaceAll"
			+ ".mnemonic").charAt(0));
		cancel = new JButton(jEdit.getProperty("common.cancel"));
		getContentPane().setLayout(new BorderLayout());
		JPanel panel = new JPanel();
		GridBagLayout layout = new GridBagLayout();
		panel.setLayout(layout);
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridwidth = constraints.gridheight = 1;
		constraints.fill = constraints.BOTH;
		JLabel label = new JLabel(jEdit.getProperty("search.find"),
			SwingConstants.RIGHT);
		layout.setConstraints(label,constraints);
		panel.add(label);
		constraints.gridx = 1;
		constraints.gridwidth = constraints.REMAINDER;
		constraints.weightx = 1.0f;
		layout.setConstraints(find,constraints);
		panel.add(find);
		constraints.gridx = 0;
		constraints.gridwidth = 1;
		constraints.gridy = 1;
		constraints.weightx = 0.0f;
		label = new JLabel(jEdit.getProperty("search.replace"),
			SwingConstants.RIGHT);
		layout.setConstraints(label,constraints);
		panel.add(label);
		constraints.gridx = 1;
		constraints.gridwidth = constraints.REMAINDER;
		constraints.weightx = 1.0f;
		layout.setConstraints(replace,constraints);
		panel.add(replace);
		getContentPane().add(BorderLayout.NORTH,panel);
		panel = new JPanel();
		panel.add(keepDialog);
		panel.add(ignoreCase);
		panel.add(regexp);
		panel.add(multifile);
		panel.add(multifileBtn);
		getContentPane().add(BorderLayout.CENTER,panel);
		panel = new JPanel();
		panel.add(findBtn);
		panel.add(replaceSelection);
		panel.add(replaceAll);
		panel.add(cancel);
		getRootPane().setDefaultButton(findBtn);
		getContentPane().add(BorderLayout.SOUTH,panel);

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		KeyHandler keyListener = new KeyHandler();
		addKeyListener(keyListener);

		find.addKeyListener(keyListener);
		replace.addKeyListener(keyListener);

		ActionHandler actionListener = new ActionHandler();
		multifile.addActionListener(actionListener);
		multifileBtn.addActionListener(actionListener);
		findBtn.addActionListener(actionListener);
		replaceSelection.addActionListener(actionListener);
		replaceAll.addActionListener(actionListener);
		cancel.addActionListener(actionListener);
		
		pack();
		GUIUtilities.loadGeometry(this,"search");

		show();
		find.requestFocus();
	}

	public void dispose()
	{
		GUIUtilities.saveGeometry(this,"search");
		super.dispose();
	}

        // private members
	private View view;
	private SearchFileSet fileset;
	private HistoryTextField find;
	private HistoryTextField replace;
	private JCheckBox keepDialog;
	private JCheckBox ignoreCase;
	private JCheckBox regexp;
	private JCheckBox multifile;
	private JButton multifileBtn;
	private JButton findBtn;
	private JButton replaceSelection;
	private JButton replaceAll;
	private JButton cancel;
	
	private void save()
	{
		find.addCurrentToHistory();
		SearchAndReplace.setSearchString(find.getText());
		replace.addCurrentToHistory();
		SearchAndReplace.setReplaceString(replace.getText());
		jEdit.setProperty("search.keepDialog.toggle",keepDialog
			.getModel().isSelected() ? "on" : "off");
		SearchAndReplace.setIgnoreCase(ignoreCase.getModel().isSelected());
		SearchAndReplace.setRegexp(regexp.getModel().isSelected());
		SearchAndReplace.setSearchFileSet(fileset);
	}

	private void disposeOrKeepDialog()
	{
		if(keepDialog.getModel().isSelected())
			return;
		dispose();
	}

	private void showMultiFileDialog()
	{
		SearchFileSet fs = new MultiFileSearchDialog(
			view,fileset).getSearchFileSet();
		if(fs != null)
		{
			fileset = fs;
		}
		multifile.getModel().setSelected(!(
			fileset instanceof CurrentBufferSet));
	}

	class ActionHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent evt)
		{
			Object source = evt.getSource();
			Buffer buffer = view.getBuffer();
			if(source == cancel)
				dispose();
			else if(source == findBtn)
			{
				save();
				if(SearchAndReplace.find(view))
					disposeOrKeepDialog();
				else
					view.getToolkit().beep();
			}
			else if(source == replaceSelection)
			{
				save();
				int selStart = view.getTextArea()
					.getSelectionStart();
				int selEnd = view.getTextArea()
					.getSelectionEnd();
				if(SearchAndReplace.replace(view,buffer,
					selStart,selEnd))
				{
					/* workaround for weird Position.Bias
					 * behaviour */
					view.getTextArea().setSelectionStart(
						selStart);
					disposeOrKeepDialog();
				}
				else
					getToolkit().beep();
			}
			else if(source == replaceAll)
			{
				save();
				if(SearchAndReplace.replaceAll(view))
					disposeOrKeepDialog();
				else
					getToolkit().beep();
			}
			else if(source == multifileBtn)
			{
				showMultiFileDialog();
			}
			else if(source == multifile)
			{
				if(multifile.getModel().isSelected())
					showMultiFileDialog();
				else
					fileset = new CurrentBufferSet();
			}
		}
	}

	class KeyHandler extends KeyAdapter
	{
		public void keyPressed(KeyEvent evt)
		{
			if(evt.getKeyCode() == KeyEvent.VK_ENTER)
			{
				save();
				if(SearchAndReplace.find(view))
					disposeOrKeepDialog();
			}
			else if(evt.getKeyCode() == KeyEvent.VK_ESCAPE)
			{
				dispose();
			}
		}
	}
}

/*
 * ChangeLog:
 * $Log$
 * Revision 1.3  1999/06/09 07:28:10  sp
 * Multifile search and replace tweaks, removed console.html
 *
 * Revision 1.2  1999/06/09 05:22:11  sp
 * Find next now supports multi-file searching, minor Perl mode tweak
 *
 * Revision 1.1  1999/06/03 08:40:03  sp
 * More cvs fixing
 *
 * Revision 1.1  1999/05/31 04:42:38  sp
 * oops #2
 *
 */
