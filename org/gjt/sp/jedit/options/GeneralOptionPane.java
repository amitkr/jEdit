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

public class GeneralOptionPane extends OptionPane
{
	public static final String METAL = "javax.swing.plaf.metal"
		+ ".MetalLookAndFeel";
	public static final String MOTIF = "com.sun.java.swing.plaf.motif"
		+ ".MotifLookAndFeel";
	public static final String WINDOWS = "com.sun.java.swing.plaf.windows"
		+ ".WindowsLookAndFeel";
	public static final String MAC = "com.sun.java.swing.plaf.mac"
		+ ".MacLookAndFeel";

	public GeneralOptionPane()
	{
		super("general");

		/* Look and feel */
		addComponent(new JLabel(jEdit.getProperty("options.general.lf.note")));

		String lf = UIManager.getLookAndFeel().getClass().getName();
		String[] lfs = { "Java", "Mac", "Motif", "Windows" };
		lookAndFeel = new JComboBox(lfs);
		if(METAL.equals(lf))
			lookAndFeel.setSelectedIndex(0);
		else if(MAC.equals(lf))
			lookAndFeel.setSelectedIndex(1);
		else if(MOTIF.equals(lf))
			lookAndFeel.setSelectedIndex(2);
		else if(WINDOWS.equals(lf))
			lookAndFeel.setSelectedIndex(3);
		addComponent(jEdit.getProperty("options.general.lf"),
			lookAndFeel);

		/* Recent file count */
		recent = new JTextField(jEdit.getProperty("recent"));
		addComponent(jEdit.getProperty("options.general.recent"),recent);

		/* History count */
		history = new JTextField(jEdit.getProperty("history"));
		addComponent(jEdit.getProperty("options.general.history"),history);

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
		saveDesktop.getModel().setSelected("on".equals(jEdit.getProperty(
			"saveDesktop")));
		addComponent(saveDesktop);

		/* Save window geometry */
		saveGeometry = new JCheckBox(jEdit.getProperty(
			"options.general.saveGeometry"));
		saveGeometry.getModel().setSelected("on".equals(jEdit.getProperty(
			"saveGeometry")));
		addComponent(saveGeometry);

		/* Show hints in status bar */
		showTips = new JCheckBox(jEdit.getProperty(
			"options.general.showTips"));
		showTips.getModel().setSelected("on".equals(jEdit.getProperty(
			"view.showTips")));
		addComponent(showTips);

		/* Show toolbar */
		showToolbar = new JCheckBox(jEdit.getProperty(
			"options.general.showToolbar"));
		showToolbar.getModel().setSelected("on".equals(jEdit.getProperty(
			"view.showToolbar")));
		addComponent(showToolbar);

		/* Show full path */
		showFullPath = new JCheckBox(jEdit.getProperty(
			"options.general.showFullPath"));
		showFullPath.getModel().setSelected("on".equals(jEdit.getProperty(
			"view.showFullPath")));
		addComponent(showFullPath);
	}

	public void save()
	{
		String lf = (String)lookAndFeel.getSelectedItem();
		if("Java".equals(lf))
			lf = METAL;
		else if("Mac".equals(lf))
			lf = MAC;
		else if("Motif".equals(lf))
			lf = MOTIF;
		else if("Windows".equals(lf))
			lf = WINDOWS;
		jEdit.setProperty("lf",lf);
		jEdit.setProperty("saveDesktop",saveDesktop.getModel()
			.isSelected() ? "on" : "off");
		jEdit.setProperty("saveGeometry",saveGeometry.getModel()
			.isSelected() ? "on" : "off");
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
		jEdit.setProperty("view.showTips",showTips.getModel()
			.isSelected() ? "on" : "off");
		jEdit.setProperty("view.showToolbar",showToolbar.getModel()
			.isSelected() ? "on" : "off");
		jEdit.setProperty("view.showFullPath",showFullPath.getModel()
			.isSelected() ? "on" : "off");
	}

	// private members
	private JComboBox lookAndFeel;
	private JTextField recent;
	private JTextField history;
	private JTextField autosave;
	private JTextField backups;
	private JTextField backupDirectory;
	private JTextField backupPrefix;
	private JTextField backupSuffix;
	private JComboBox lineSeparator;
	private JCheckBox saveDesktop;
	private JCheckBox saveGeometry;
	private JCheckBox showTips;
	private JCheckBox showToolbar;
	private JCheckBox showFullPath;
}
