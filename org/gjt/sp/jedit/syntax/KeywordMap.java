/*
 * KeywordMap.java - Fast keyword->id map
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
package org.gjt.sp.jedit.syntax;

import javax.swing.text.Segment;
import org.gjt.sp.jedit.jEdit;

public class KeywordMap
{
	// public members
	public KeywordMap(boolean ignoreCase)
	{
		map = new Keyword[27];
		this.ignoreCase = ignoreCase;
	}

	public String lookup(Segment text, int offset, int length)
	{
		if(length == 0)
			return null;
		char key = text.array[offset];
		if(!Character.isLetter(key))
			key = 26;
		else
			key = (char)(Character.toUpperCase(key) - 'A');
		Keyword k = map[key];
		while(k != null)
		{
			String keyword = k.keyword;
			if(length != keyword.length())
			{
				k = k.next;
				continue;
			}
			if(jEdit.regionMatches(ignoreCase,text,offset,keyword))
				return k.id;
			k = k.next;
		}
		return null;
	}

	public void add(String keyword, String id)
	{
		char key = keyword.charAt(0);
		if(!Character.isLetter(key))
			key = 26;
		else
			key = (char)(Character.toUpperCase(key) - 'A');
		map[key] = new Keyword(keyword,id,map[key]);
	}

	// private members
	private class Keyword
	{
		public Keyword(String keyword, String id, Keyword next)
		{
			this.keyword = keyword;
			this.id = id;
			this.next = next;
		}

		public String keyword;
		public String id;
		public Keyword next;
	}

	private Keyword[] map;
	private boolean ignoreCase;
}
