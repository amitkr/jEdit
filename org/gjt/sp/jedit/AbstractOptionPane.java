/*
 * AbstractOptionPane.java - Abstract option pane
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

import javax.swing.*;
import java.awt.*;

/**
 * The default implementation of the option pane interface.
 *
 * @see org.gjt.sp.jedit.OptionPane
 */
public class AbstractOptionPane extends JPanel implements OptionPane
{
	/**
	 * Creates a new option pane.
	 * @param name The internal name
	 */
	public AbstractOptionPane(String name)
	{
		this.name = name;
		setLayout(gridBag = new GridBagLayout());
	}

	/**
	 * Returns the internal name of this option pane.
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the component that should be displayed for this option pane.
	 */
	public Component getComponent()
	{
		return this;
	}

	/**
	 * Called when the options dialog's `ok' button is closed.
	 * This should save any properties saved in this option
	 * pane.
	 */
	public void save() {}

	// protected members

	/**
	 * The layout manager.
	 */
	protected GridBagLayout gridBag;

	/**
	 * The number of components already added to the layout manager.
	 */
	protected int y;

	/**
	 * Adds a labeled component to the option pane.
	 * @param label The label
	 * @param comp The component
	 */
	protected void addComponent(String label, Component comp)
	{
		GridBagConstraints cons = new GridBagConstraints();
		cons.gridy = y++;
		cons.gridheight = 1;
		cons.gridwidth = 3;
		cons.fill = GridBagConstraints.BOTH;
		cons.weightx = 1.0f;

		cons.gridx = 0;
		JLabel l = new JLabel(label,SwingConstants.RIGHT);
		gridBag.setConstraints(l,cons);
		add(l);

		cons.gridx = 3;
		cons.gridwidth = 1;
		gridBag.setConstraints(comp,cons);
		add(comp);
	}

	/**
	 * Adds a component to the option pane.
	 * @param comp The component
	 */
	protected void addComponent(Component comp)
	{
		GridBagConstraints cons = new GridBagConstraints();
		cons.gridy = y++;
		cons.gridheight = 1;
		cons.gridwidth = cons.REMAINDER;
		cons.fill = GridBagConstraints.NONE;
		cons.anchor = GridBagConstraints.WEST;
		cons.weightx = 1.0f;

		gridBag.setConstraints(comp,cons);
		add(comp);
	}

	// private members
	private String name;
}

/*
 * ChangeLog:
 * $Log$
 * Revision 1.1  1999/10/04 03:20:50  sp
 * Option pane change, minor tweaks and bug fixes
 *
 */
