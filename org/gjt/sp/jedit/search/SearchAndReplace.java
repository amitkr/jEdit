/*
 * SearchAndReplace.java - Search and replace
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

package org.gjt.sp.jedit.search;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.JOptionPane;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.*;

/**
 * Class that implements regular expression and literal search within
 * jEdit buffers.
 * @author Slava Pestov
 * @version $Id$
 */
public class SearchAndReplace
{
	/**
	 * Sets the current search string.
	 * @param search The new search string
	 */
	public static void setSearchString(String search)
	{
		SearchAndReplace.search = search;
		matcher = null;
	}

	/**
	 * Returns the current search string.
	 */
	public static String getSearchString()
	{
		return search;
	}

	/**
	 * Sets the current replacement string.
	 * @param search The new replacement string
	 */
	public static void setReplaceString(String replace)
	{
		SearchAndReplace.replace = replace;
		matcher = null;
	}

	/**
	 * Returns the current replacement string.
	 */
	public static String getReplaceString()
	{
		return replace;
	}

	/**
	 * Sets the ignore case flag.
	 * @param ignoreCase True if searches should be case insensitive,
	 * false otherwise
	 */
	public static void setIgnoreCase(boolean ignoreCase)
	{
		SearchAndReplace.ignoreCase = ignoreCase;
		matcher = null;
	}

	/**
	 * Returns the state of the ignore case flag.
	 * @return True if searches should be case insensitive,
	 * false otherwise
	 */
	public static boolean getIgnoreCase()
	{
		return ignoreCase;
	}

	/**
	 * Sets the state of the regular expression flag.
	 * @param regexp True if regular expression searches should be
	 * performed
	 */
	public static void setRegexp(boolean regexp)
	{
		SearchAndReplace.regexp = regexp;
		matcher = null;
	}

	/**
	 * Returns the state of the regular expression flag.
	 * @return True if regular expression searches should be performed
	 */
	public static boolean getRegexp()
	{
		return regexp;
	}

	/**
	 * Sets the current search string matcher. Note that calling
	 * <code>setSearchString</code>, <code>setReplaceString</code>,
	 * <code>setIgnoreCase</code> or <code>setRegExp</code> will
	 * reset the matcher to the default.
	 */
	public static void setSearchMatcher(SearchMatcher matcher)
	{
		SearchAndReplace.matcher = matcher;
	}

	/**
	 * Returns the current search string matcher.
	 * @exception IllegalArgumentException if regular expression search
	 * is enabled, the search string or replacement string is invalid
	 */
	public static SearchMatcher getSearchMatcher()
		throws IllegalArgumentException
	{
		if(matcher != null)
			return matcher;

		if(search == null || "".equals(search))
			return null;

		if(regexp)
			return new RESearchMatcher(search,replace,ignoreCase);
		else
			return new LiteralSearchMatcher(search,replace,ignoreCase);
	}

	/**
	 * Sets the current search file set.
	 * @param fileset The file set to perform searches in
	 */
	public static void setSearchFileSet(SearchFileSet fileset)
	{
		SearchAndReplace.fileset = fileset;
	}

	/**
	 * Returns the current search file set.
	 */
	public static SearchFileSet getSearchFileSet()
	{
		return fileset;
	}

	/**
	 * Finds the next occurance of the search string.
	 * @param view The view
	 * @return True if the operation was successful, false otherwise
	 */
	public static boolean find(View view)
	{
		boolean repeat = false;
		Buffer buffer = null;

		SearchMatcher matcher = getSearchMatcher();
		if(matcher == null)
		{
			view.getToolkit().beep();
			return false;
		}

		try
		{
loop:			for(;;)
			{
				while((buffer = (repeat ? fileset.getFirstBuffer(view)
					: fileset.getNextBuffer(view,buffer))) != null)
				{
					int start;
					if(view.getBuffer() == buffer && !repeat)
						start = view.getTextArea()
							.getSelectionEnd();
					else
						start = 0;
					if(find(view,buffer,start))
						return true;
					repeat = false;
				}

				int result = JOptionPane.showConfirmDialog(view,
					jEdit.getProperty("keepsearching.message"),
					jEdit.getProperty("keepsearching.title"),
					JOptionPane.YES_NO_OPTION,
					JOptionPane.QUESTION_MESSAGE);
				if(result == JOptionPane.YES_OPTION)
				{
					// start search from beginning
					buffer = null;
					repeat = true;
				}
				else
					return false;
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			Object[] args = { e.getMessage() };
			if(args[0] == null)
				args[0] = e.toString();
			GUIUtilities.error(view,"searcherror",args);
		}

		return false;
	}

	/**
	 * Finds the next instance of the search string in the specified buffer.
	 * @param view The view
	 * @param buffer The buffer
	 * @param start Location where to start the search
	 * @exception BadLocationException if `start' is invalid
	 * @exception IllegalArgumentException if regular expression search
	 * is enabled, the search string or replacement string is invalid
	 */
	public static boolean find(View view, Buffer buffer, int start)
		throws BadLocationException, IllegalArgumentException
	{
		SearchMatcher matcher = getSearchMatcher();

		String text = buffer.getText(start,
			buffer.getLength() - start);
		int[] match = matcher.nextMatch(text);
		if(match != null)
		{
			view.setBuffer(buffer);
			view.getTextArea().select(start + match[0],
					start + match[1]);
			return true;
		}
		else
			return false;
	}

	/**
	 * Replaces the current selection with the replacement string.
	 * @param view The view
	 * @param buffer The buffer
	 * @return True if the operation was successful, false otherwise
	 */
	public static boolean replace(View view, Buffer buffer)
	{
		JEditTextArea textArea = view.getTextArea();
		int selStart = textArea.getSelectionStart();
		int selEnd = textArea.getSelectionEnd();
		if(selStart == selEnd)
		{
			view.getToolkit().beep();
			return false;
		}
		boolean retVal = replace(view,buffer,selStart,selEnd);
		textArea.setSelectionStart(selStart);
		return retVal;
	}

	/**
	 * Replaces all occurances of the search string with the replacement
	 * string.
	 */
	public static boolean replaceAll(View view)
	{
		boolean retval = false;
		Buffer[] buffers = fileset.getSearchBuffers(view);
		for(int i = 0; i < buffers.length; i++)
		{
			Buffer buffer = buffers[i];
			retval |= replace(view,buffer,0,buffer.getLength());
		}
		return retval;
	}

	/**
	 * Replaces all occurances of the search string with the replacement
	 * string.
	 * @param view The view
	 * @param buffer The buffer
	 * @param start The index where to start the search
	 * @param end The end offset of the search
	 * @return True if the replace operation was successful, false
	 * if no matches were found
	 */
	public static boolean replace(View view, Buffer buffer,
		int start, int end)
	{
		if(!view.getTextArea().isEditable())
		{
			view.getToolkit().beep();
			return false;
		}
		boolean found = false;
		buffer.beginCompoundEdit();
		try
		{
			SearchMatcher matcher = getSearchMatcher();
			if(matcher == null)
			{
				view.getToolkit().beep();
				return false;
			}

			int[] match;

			Element map = buffer.getDefaultRootElement();
			int startLine = map.getElementIndex(start);
			int endLine = map.getElementIndex(end);

			for(int i = startLine; i <= endLine; i++)
			{
				Element lineElement = map.getElement(i);
				int lineStart;
				int lineEnd;

				if(i == startLine)
					lineStart = start;
				else
					lineStart = lineElement.getStartOffset();

				if(i == endLine)
					lineEnd = end;
				else
					lineEnd = lineElement.getEndOffset() - 1;

				lineEnd -= lineStart;
				String line = buffer.getText(lineStart,lineEnd);
				String newLine = matcher.substitute(line);
				if(newLine == null)
					continue;
				buffer.remove(lineStart,lineEnd);
				buffer.insertString(lineStart,newLine,null);

				end += (newLine.length() - lineEnd);
				found = true;
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			found = false;
			Object[] args = { e.getMessage() };
			if(args[0] == null)
				args[0] = e.toString();
			GUIUtilities.error(view,"searcherror",args);
		}
		buffer.endCompoundEdit();
		return found;
	}

	/**
	 * Loads search and replace state from the properties.
	 */
	public static void load()
	{
		fileset = new CurrentBufferSet();
		search = jEdit.getProperty("search.find.value");
		replace = jEdit.getProperty("search.replace.value");
		regexp = "on".equals(jEdit.getProperty("search.regexp.toggle"));
		ignoreCase = "on".equals(jEdit.getProperty("search.ignoreCase.toggle"));
	}

	/**
	 * Saves search and replace state to the properties.
	 */
	public static void save()
	{
		jEdit.setProperty("search.find.value",(search == null ? ""
			: search));
		jEdit.setProperty("search.replace.value",(replace == null ? ""
			: replace));
		jEdit.setProperty("search.ignoreCase.toggle",
			ignoreCase ? "on" : "off");
		jEdit.setProperty("search.regexp.toggle",
			regexp ? "on" : "off");
	}
		
	// private members
	private static String search;
	private static String replace;
	private static boolean regexp;
	private static boolean ignoreCase;
	private static SearchMatcher matcher;
	private static SearchFileSet fileset;
}

/*
 * ChangeLog:
 * $Log$
 * Revision 1.9  1999/07/05 04:38:39  sp
 * Massive batch of changes... bug fixes, also new text component is in place.
 * Have fun
 *
 * Revision 1.8  1999/06/12 02:30:27  sp
 * Find next can now perform multifile searches, multifile-search command added,
 * new style option pane
 *
 * Revision 1.7  1999/06/09 07:28:10  sp
 * Multifile search and replace tweaks, removed console.html
 *
 * Revision 1.6  1999/06/09 05:22:11  sp
 * Find next now supports multi-file searching, minor Perl mode tweak
 *
 * Revision 1.5  1999/06/06 05:05:25  sp
 * Search and replace tweaks, Perl/Shell Script mode updates
 *
 * Revision 1.4  1999/06/03 08:24:13  sp
 * Fixing broken CVS
 *
 * Revision 1.4  1999/05/31 04:38:51  sp
 * Syntax optimizations, HyperSearch for Selection added (Mike Dillon)
 *
 * Revision 1.3  1999/05/30 01:28:43  sp
 * Minor search and replace updates
 *
 * Revision 1.2  1999/05/29 08:06:56  sp
 * Search and replace overhaul
 *
 */
