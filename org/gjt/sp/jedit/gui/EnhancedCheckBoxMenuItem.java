/*
 * EnhancedCheckBoxMenuItem.java - Check box menu item
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

package org.gjt.sp.jedit.gui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.*;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.EditAction;
import org.gjt.sp.jedit.View;

/**
 * Mega hackery in this class.
 * <ul>
 * <li>Painting of custom strings (for the multi-key accelerators)
 * <li>Support for null action commands
 * <li>Support for repeating and recording menu items by sending all
 * action execution to the text area's input handler (this also
 * allows us to add text area actions to the menu bar)
 * </ul>
 */
public class EnhancedCheckBoxMenuItem extends JCheckBoxMenuItem
{
	public EnhancedCheckBoxMenuItem(String label, String keyBinding,
		EditAction action)
	{
		super(label);
		this.keyBinding = keyBinding;
		this.action = action;

		setModel(new Model());
		setEnabled(action != null);

		acceleratorFont = UIManager
			.getFont("MenuItem.acceleratorFont");
		acceleratorForeground = UIManager
			.getColor("MenuItem.acceleratorForeground");
		acceleratorSelectionForeground = UIManager
			.getColor("MenuItem.acceleratorSelectionForeground");
	}

	public Dimension getPreferredSize()
	{
		Dimension d = super.getPreferredSize();
		if(keyBinding != null)
		{
			d.width += (getToolkit().getFontMetrics(acceleratorFont)
				.stringWidth(keyBinding) + 30);
		}
		return d;
	}

	public void paint(Graphics g)
	{
		super.paint(g);
		if(keyBinding != null)
		{
			g.setFont(acceleratorFont);
			g.setColor(getModel().isArmed() ?
				acceleratorSelectionForeground :
				acceleratorForeground);
			FontMetrics fm = g.getFontMetrics();
			Insets insets = getInsets();
			g.drawString(keyBinding,getWidth() - (fm.stringWidth(
				keyBinding) + insets.right + insets.left),
				getFont().getSize() + (insets.top - 1)
				/* XXX magic number */);
		}
	}

	public String getActionCommand()
	{
		return getModel().getActionCommand();
	}

	public void fireActionPerformed(ActionEvent evt)
	{
		if(action != null)
		{
			// Get the view that owns us
			View view = EditAction.getView(this);
			JEditTextArea textArea = view.getTextArea();
			textArea.getInputHandler().executeAction(
				action,textArea,getActionCommand());
		}
	}

	// private members
	private String keyBinding;
	private EditAction action;
	private Font acceleratorFont;
	private Color acceleratorForeground;
	private Color acceleratorSelectionForeground;

	class Model extends DefaultButtonModel
	{
		public boolean isSelected()
		{
			return action.isSelected(EnhancedCheckBoxMenuItem.this);
		}

		public void setSelected(boolean b) {}
	}
}
