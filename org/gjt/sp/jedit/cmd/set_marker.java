/*
 * set_marker.java - Command
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

package org.gjt.sp.jedit.cmd;

import javax.swing.*;
import java.util.Hashtable;
import org.gjt.sp.jedit.syntax.SyntaxTextArea;
import org.gjt.sp.jedit.*;

public class set_marker implements Command
{
	public void exec(Buffer buffer, View view, String arg, Hashtable args)
	{
		if(buffer.isReadOnly())
			view.getToolkit().beep();
		SyntaxTextArea textArea = view.getTextArea();
		if(arg == null)
		{
			arg = (String)JOptionPane.showInputDialog(view,
				jEdit.props.getProperty("setmarker.message"),
				jEdit.props.getProperty("setmarker.title"),
				JOptionPane.QUESTION_MESSAGE,null,null,
				textArea.getSelectedText());
		}
		if(arg != null)
			buffer.addMarker(arg,textArea.getSelectionStart(),
				textArea.getSelectionEnd());
	}
}
