/*
 * GeneralOptionPane.java - General options panel
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

package org.gjt.sp.jedit.options;

import javax.swing.*;
import java.awt.*;
import org.gjt.sp.jedit.*;

public class GeneralOptionPane extends AbstractOptionPane
{
	public GeneralOptionPane()
	{
		super("general");
	}

	// protected members
	protected void _init()
	{
		/* Look and feel */
		lfs = UIManager.getInstalledLookAndFeels();
		String[] names = new String[lfs.length];
		String lf = UIManager.getLookAndFeel().getClass().getName();
		int index = 0;
		for(int i = 0; i < names.length; i++)
		{
			names[i] = lfs[i].getName();
			if(lf.equals(lfs[i].getClassName()))
				index = i;
		}

		lookAndFeel = new JComboBox(names);
		lookAndFeel.setSelectedIndex(index);

		addComponent(jEdit.getProperty("options.general.lf"),
			lookAndFeel);

		/* Recent file count */
		recent = new JTextField(jEdit.getProperty("recent"));
		addComponent(jEdit.getProperty("options.general.recent"),recent);

		/* History count */
		history = new JTextField(jEdit.getProperty("history"));
		addComponent(jEdit.getProperty("options.general.history"),history);

		/* Default file encoding */
		String[] encodings = {
			"ASCII", "8859_1", "UTF8", "Cp850", "Cp1252",
			"MacRoman", "KOI8_R", "Unicode"
		};

		encoding = new JComboBox(encodings);
		encoding.setEditable(true);
		encoding.setSelectedItem(jEdit.getProperty("buffer.encoding",
			System.getProperty("file.encoding")));
		addComponent(jEdit.getProperty("options.general.encoding"),encoding);

		/* Autosave interval */
		autosave = new JTextField(jEdit.getProperty("autosave"));
		addComponent(jEdit.getProperty("options.general.autosave"),autosave);

		/* Backup count */
		backups = new JTextField(jEdit.getProperty("backups"));
		addComponent(jEdit.getProperty("options.general.backups"),backups);

		/* Backup directory */
		backupDirectory = new JTextField(jEdit.getProperty(
			"backup.directory"));
		addComponent(jEdit.getProperty("options.general.backupDirectory"),
			backupDirectory);

		/* Backup filename prefix */
		backupPrefix = new JTextField(jEdit.getProperty("backup.prefix"));
		addComponent(jEdit.getProperty("options.general.backupPrefix"),
			backupPrefix);

		/* Backup suffix */
		backupSuffix = new JTextField(jEdit.getProperty(
			"backup.suffix"));
		addComponent(jEdit.getProperty("options.general.backupSuffix"),
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
		addComponent(jEdit.getProperty("options.general.lineSeparator"),
			lineSeparator);

		/* Session management */
		saveDesktop = new JCheckBox(jEdit.getProperty(
			"options.general.saveDesktop"));
		saveDesktop.getModel().setSelected(jEdit.getBooleanProperty(
			"saveDesktop"));
		addComponent(saveDesktop);

		/* Show buffer tabs */
		showBufferTabs = new JCheckBox(jEdit.getProperty(
			"options.general.showBufferTabs"));
		showBufferTabs.getModel().setSelected(jEdit.getBooleanProperty(
			"view.showBufferTabs"));
		addComponent(showBufferTabs);

		/* Buffer tabs position */
		String[] positions = {
			"top", "left", "bottom", "right"
		};

		bufferTabsPos = new JComboBox(positions);
		bufferTabsPos.setSelectedIndex(Integer.parseInt(jEdit.getProperty(
			"view.bufferTabsPos")) - 1);
		addComponent(jEdit.getProperty("options.general.bufferTabsPos"),
			bufferTabsPos);

		/* Show full path */
		showFullPath = new JCheckBox(jEdit.getProperty(
			"options.general.showFullPath"));
		showFullPath.getModel().setSelected(jEdit.getBooleanProperty(
			"view.showFullPath"));
		addComponent(showFullPath);

		/* Sort buffer list */
		sortBuffers = new JCheckBox(jEdit.getProperty(
			"options.general.sortBuffers"));
		sortBuffers.getModel().setSelected(jEdit.getBooleanProperty(
			"sortBuffers"));
		addComponent(sortBuffers);

		/* Sort buffers by names */
		sortByName = new JCheckBox(jEdit.getProperty(
			"options.general.sortByName"));
		sortByName.getModel().setSelected(jEdit.getBooleanProperty(
			"sortByName"));
		addComponent(sortByName);

		/* Check mod status on focus */
		checkModStatus = new JCheckBox(jEdit.getProperty(
			"options.general.checkModStatus"));
		checkModStatus.getModel().setSelected(jEdit.getBooleanProperty(
			"view.checkModStatus"));
		addComponent(checkModStatus);

		/* Tokenize files on load */
		tokenize = new JCheckBox(jEdit.getProperty(
			"options.general.tokenize"));
		tokenize.getModel().setSelected(jEdit.getBooleanProperty(
			"buffer.tokenize"));
		addComponent(tokenize);
	}

	protected void _save()
	{
		String lf = lfs[lookAndFeel.getSelectedIndex()].getClassName();
		jEdit.setProperty("lookAndFeel",lf);
		jEdit.setBooleanProperty("saveDesktop",saveDesktop.getModel()
			.isSelected());
		jEdit.setProperty("buffer.encoding",(String)
			encoding.getSelectedItem());
		jEdit.setProperty("autosave",autosave.getText());
		jEdit.setProperty("recent",recent.getText());
		jEdit.setProperty("history",history.getText());
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
		jEdit.setBooleanProperty("view.showBufferTabs",showBufferTabs.getModel()
			.isSelected());
		jEdit.setProperty("view.bufferTabsPos",String.valueOf(
			bufferTabsPos.getSelectedIndex() + 1));
		jEdit.setBooleanProperty("view.showFullPath",showFullPath.getModel()
			.isSelected());
		jEdit.setBooleanProperty("sortBuffers",sortBuffers.getModel()
			.isSelected());
		jEdit.setBooleanProperty("sortByName",sortByName.getModel()
			.isSelected());
		jEdit.setBooleanProperty("view.checkModStatus",checkModStatus.getModel()
			.isSelected());
		jEdit.setBooleanProperty("buffer.tokenize",tokenize.getModel()
			.isSelected());
	}

	// private members
	private UIManager.LookAndFeelInfo[] lfs;
	private JComboBox lookAndFeel;
	private JTextField recent;
	private JTextField history;
	private JComboBox encoding;
	private JTextField autosave;
	private JTextField backups;
	private JTextField backupDirectory;
	private JTextField backupPrefix;
	private JTextField backupSuffix;
	private JComboBox lineSeparator;
	private JCheckBox saveDesktop;
	private JCheckBox showBufferTabs;
	private JComboBox bufferTabsPos;
	private JCheckBox showFullPath;
	private JCheckBox sortBuffers;
	private JCheckBox sortByName;
	private JCheckBox checkModStatus;
	private JCheckBox tokenize;
}
