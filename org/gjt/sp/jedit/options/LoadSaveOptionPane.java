/*
 * LoadSaveOptionPane.java - Loading and saving options panel
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

package org.gjt.sp.jedit.options;

import javax.swing.*;
import java.awt.*;
import org.gjt.sp.jedit.*;

public class LoadSaveOptionPane extends AbstractOptionPane
{
	public LoadSaveOptionPane()
	{
		super("loadsave");
	}

	// protected members
	protected void _init()
	{
		/* Session management */
		saveDesktop = new JCheckBox(jEdit.getProperty(
			"options.loadsave.saveDesktop"));
		saveDesktop.setSelected(jEdit.getBooleanProperty("saveDesktop"));
		addComponent(saveDesktop);

		/* Default file encoding */
		String[] encodings = {
			"ASCII", "8859_1", "UTF8", "Cp850", "Cp1252",
			"MacRoman", "KOI8_R", "Unicode"
		};

		encoding = new JComboBox(encodings);
		encoding.setEditable(true);
		encoding.setSelectedItem(jEdit.getProperty("buffer.encoding",
			System.getProperty("file.encoding")));
		addComponent(jEdit.getProperty("options.loadsave.encoding"),encoding);

		/* Autosave interval */
		autosave = new JTextField(jEdit.getProperty("autosave"));
		addComponent(jEdit.getProperty("options.loadsave.autosave"),autosave);

		/* Backup count */
		backups = new JTextField(jEdit.getProperty("backups"));
		addComponent(jEdit.getProperty("options.loadsave.backups"),backups);

		/* Backup directory */
		backupDirectory = new JTextField(jEdit.getProperty(
			"backup.directory"));
		addComponent(jEdit.getProperty("options.loadsave.backupDirectory"),
			backupDirectory);

		/* Backup filename prefix */
		backupPrefix = new JTextField(jEdit.getProperty("backup.prefix"));
		addComponent(jEdit.getProperty("options.loadsave.backupPrefix"),
			backupPrefix);

		/* Backup suffix */
		backupSuffix = new JTextField(jEdit.getProperty(
			"backup.suffix"));
		addComponent(jEdit.getProperty("options.loadsave.backupSuffix"),
			backupSuffix);

		/* Line separator */
		String[] lineSeps = { jEdit.getProperty("lineSep.unix"),
			jEdit.getProperty("lineSep.windows"),
			jEdit.getProperty("lineSep.mac") };
		lineSeparator = new JComboBox(lineSeps);
		String lineSep = jEdit.getProperty("buffer.lineSeparator",
			System.getProperty("line.separator"));
		if("\n".equals(lineSep))
			lineSeparator.setSelectedIndex(0);
		else if("\r\n".equals(lineSep))
			lineSeparator.setSelectedIndex(1);
		else if("\r".equals(lineSep))
			lineSeparator.setSelectedIndex(2);
		addComponent(jEdit.getProperty("options.loadsave.lineSeparator"),
			lineSeparator);

		/* Number of I/O threads to start */
		ioThreadCount = new JTextField(jEdit.getProperty("ioThreadCount"));
		addComponent(jEdit.getProperty("options.loadsave.ioThreadCount"),
			ioThreadCount);
	}

	public void _save()
	{
		jEdit.setBooleanProperty("saveDesktop",saveDesktop.isSelected());
		jEdit.setProperty("buffer.encoding",(String)
			encoding.getSelectedItem());
		jEdit.setProperty("autosave",autosave.getText());
		jEdit.setProperty("backups",backups.getText());
		jEdit.setProperty("backup.directory",backupDirectory.getText());
		jEdit.setProperty("backup.prefix",backupPrefix.getText());
		jEdit.setProperty("backup.suffix",backupSuffix.getText());
		String lineSep = null;
		switch(lineSeparator.getSelectedIndex())
		{
		case 0:
			lineSep = "\n";
			break;
		case 1:
			lineSep = "\r\n";
			break;
		case 2:
			lineSep = "\r";
			break;
		}
		jEdit.setProperty("buffer.lineSeparator",lineSep);
		jEdit.setProperty("ioThreadCount",ioThreadCount.getText());
	}

	// private members
	private JCheckBox saveDesktop;
	private JComboBox encoding;
	private JTextField autosave;
	private JTextField backups;
	private JTextField backupDirectory;
	private JTextField backupPrefix;
	private JTextField backupSuffix;
	private JComboBox lineSeparator;
	private JTextField ioThreadCount;
}

/*
 * Change Log:
 * $Log$
 * Revision 1.2  2000/12/03 08:16:18  sp
 * Documentation updates
 *
 * Revision 1.1  2000/11/07 10:08:32  sp
 * Options dialog improvements, documentation changes, bug fixes
 *
 */
