/*
 * RESearchMatcher.java - Regular expression matcher
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

import gnu.regexp.*;
import javax.swing.text.Segment;
import org.gjt.sp.jedit.MiscUtilities;

/**
 * A regular expression string matcher.
 * @author Slava Pestov
 * @version $Id$
 */
public class RESearchMatcher implements SearchMatcher
{
	/**
	 * Creates a new regular expression string matcher.
	 * @param search The search string
	 * @param replace The replacement string
	 * @param ignoreCase True if the matcher should be case insensitive,
	 * false otherwise
	 */
	public RESearchMatcher(String search, String replace,
		boolean ignoreCase)
	{
		// gnu.regexp doesn't seem to support \n and \t in the replace
		// string, so implement it here
		this.replace = MiscUtilities.escapesToChars(replace);

		try
		{
			re = new RE(search,(ignoreCase ? RE.REG_ICASE : 0)
				| RE.REG_MULTILINE,RESyntax.RE_SYNTAX_PERL5);
		}
		catch(REException e)
		{
			throw new IllegalArgumentException(e.getMessage());
		}
	}

	/**
	 * Returns the offset of the first match of the specified text
	 * within this matcher.
	 * @param text The text to search in
	 * @return an array where the first element is the start offset
	 * of the match, and the second element is the end offset of
	 * the match
	 */
	public int[] nextMatch(Segment text)
	{
		REMatch match = re.getMatch(text);
		if(match == null)
			return null;
		int[] result = { match.getStartIndex(),
			match.getEndIndex() };
		return result;
	}

	/**
	 * Returns the specified text, with any substitution specified
	 * within this matcher performed.
	 * @param text The text
	 */
	public String substitute(String text)
	{
		return re.substituteAll(text,replace);
	}

	// private members
	private String replace;
	private RE re;
}

/*
 * ChangeLog:
 * $Log$
 * Revision 1.4  2000/11/07 10:08:32  sp
 * Options dialog improvements, documentation changes, bug fixes
 *
 * Revision 1.3  1999/06/06 05:05:25  sp
 * Search and replace tweaks, Perl/Shell Script mode updates
 *
 * Revision 1.2  1999/05/30 01:28:43  sp
 * Minor search and replace updates
 *
 * Revision 1.1  1999/05/29 08:06:56  sp
 * Search and replace overhaul
 *
 */
