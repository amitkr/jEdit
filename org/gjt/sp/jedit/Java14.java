/*
 * Java14.java - Java 2 version 1.4 API calls
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2001, 2002 Slava Pestov
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
import javax.swing.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.*;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.util.Log;
//}}}

/**
 * This file must be compiled with a JDK 1.4 or higher javac. If you are using
 * an older Java version and wish to compile from source, you can safely leave
 * this file out.
 * @since jEdit 4.0pre4
 * @author Slava Pestov
 * @version $Id$
 */
public class Java14
{
	//{{{ init() method
	public static void init()
	{
		JFrame.setDefaultLookAndFeelDecorated(
			jEdit.getBooleanProperty("decorate.frames"));
		JDialog.setDefaultLookAndFeelDecorated(
			jEdit.getBooleanProperty("decorate.dialogs"));

		KeyboardFocusManager.setCurrentKeyboardFocusManager(
			new MyFocusManager());

		EditBus.addToBus(new EBComponent()
		{
			public void handleMessage(EBMessage msg)
			{
				if(msg instanceof ViewUpdate)
				{
					ViewUpdate vu = (ViewUpdate)msg;
					if(vu.getWhat() == ViewUpdate.CREATED)
					{
						vu.getView().setFocusTraversalPolicy(
							new MyFocusTraversalPolicy());
					}
				}
			}
		});

		Clipboard selection = Toolkit.getDefaultToolkit().getSystemSelection();
		if(selection != null)
		{
			Log.log(Log.DEBUG,Java14.class,"Setting % register"
				+ " to system selection");
			Registers.setRegister('%',new Registers.ClipboardRegister(selection));
		}
	} //}}}

	//{{{ MyFocusManager class
	static class MyFocusManager extends DefaultKeyboardFocusManager
	{
		MyFocusManager()
		{
			setDefaultFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
		}

		public boolean postProcessKeyEvent(KeyEvent evt)
		{
			if(!evt.isConsumed())
			{
				Component comp = (Component)evt.getSource();
				if(!comp.isShowing())
					return true;

				for(;;)
				{
					if(comp instanceof View)
					{
						((View)comp).processKeyEvent(evt);
						return true;
					}
					else if(comp == null || comp instanceof Window
						|| comp instanceof JEditTextArea)
					{
						break;
					}
					else
						comp = comp.getParent();
				}
			}

			return super.postProcessKeyEvent(evt);
		}
	} //}}}

	//{{{ MyFocusTraversalPolicy class
	static class MyFocusTraversalPolicy extends LayoutFocusTraversalPolicy
	{
		public Component getDefaultComponent(Container focusCycleRoot)
		{
			return ((View)focusCycleRoot).getTextArea();
		}
	} //}}}
}
