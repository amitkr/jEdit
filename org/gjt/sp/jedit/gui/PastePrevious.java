/*
 * PastePrevious.java - Paste previous dialog
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

package org.gjt.sp.jedit.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;
import org.gjt.sp.jedit.*;

public class PastePrevious extends JDialog
implements ActionListener, KeyListener
{
	public PastePrevious(View view)
	{
		super(view,jEdit.getProperty("pasteprev.title"),true);
		this.view = view;
		Container content = getContentPane();
		clipHistory = jEdit.getClipHistory();
		String[] abbrevClipHistory = new String[clipHistory.size()];
		for(int i = 0, j = clipHistory.size() - 1;
			i < clipHistory.size(); i++, j--)
		{
			String clip = (String)clipHistory.elementAt(i);
			clip = clip.replace('\n',' ');
			if(clip.length() > 60)
			{
				clip = clip.substring(0,30) + " ... "
					+ clip.substring(clip.length() - 30);
			}
			abbrevClipHistory[j] = clip;
		}
		clips = new JList(abbrevClipHistory);
		clips.setVisibleRowCount(10);
		clips.setFont(view.getTextArea().getFont());
		insert = new JButton(jEdit.getProperty("pasteprev.insert"));
		cancel = new JButton(jEdit.getProperty("pasteprev.cancel"));
		content.setLayout(new BorderLayout());
		content.add("North",new JLabel(jEdit.getProperty("pasteprev.caption")));
		content.add("Center",new JScrollPane(clips));
		JPanel panel = new JPanel();
		panel.add(insert);
		panel.add(cancel);
		content.add("South",panel);
		getRootPane().setDefaultButton(insert);
		insert.addKeyListener(this);
		insert.addActionListener(this);
		cancel.addKeyListener(this);
		cancel.addActionListener(this);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		Dimension screen = getToolkit().getScreenSize();
		pack();
		setLocation((screen.width - getSize().width) / 2,
			(screen.height - getSize().height) / 2);
		show();
	}

	public void actionPerformed(ActionEvent evt)
	{
		Object source = evt.getSource();
		if(evt.getSource() == insert)
			doInsert();
		else if(evt.getSource() == cancel)
			dispose();
	}

	public void keyPressed(KeyEvent evt)
	{
		switch(evt.getKeyCode())
		{
		case KeyEvent.VK_ENTER:
			doInsert();
			break;
		case KeyEvent.VK_ESCAPE:
			dispose();
			break;
		}
	}

	public void keyReleased(KeyEvent evt) {}
	public void keyTyped(KeyEvent evt) {}

	// private members
	private View view;
	private JList clips;
	private Vector clipHistory;
	private JButton insert;
	private JButton cancel;

	private void doInsert()
	{
		int selected = clips.getSelectedIndex();
		if(selected != -1)
		{
			String clip = (String)clipHistory.elementAt(
				clipHistory.size() - selected - 1);
			clipHistory.removeElementAt(selected);
			clipHistory.addElement(clip);
			view.getTextArea().replaceSelection(clip);
		}
		dispose();
	}
}
