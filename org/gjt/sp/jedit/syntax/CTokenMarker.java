/*
 * CTokenMarker.java - C/C++/Java token marker
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
package org.gjt.sp.jedit.syntax;

import javax.swing.text.Segment;

public class CTokenMarker extends TokenMarker
{
	// public members
	public CTokenMarker(boolean cpp, KeywordMap keywords)
	{
		this.cpp = cpp;
		this.keywords = keywords;
	}

	public Token markTokens(Segment line, int lineIndex)
	{
		ensureCapacity(lineIndex);
		lastToken = null;
		String token = lineIndex == 0 ? null : lineInfo[lineIndex - 1];
		int offset = line.offset;
		int lastOffset = offset;
		int lastKeyword = offset;
		int length = line.count + offset;
		boolean backslash = false;
loop:		for(int i = offset; i < length; i++)
		{
			switch(line.array[i])
			{
			case '\\':
				backslash = !backslash;
				break;
			case ';': case '.': case ',': case ' ': case '\t':
			case '(': case ')': case '[': case ']':
				backslash = false;
				if(token == null)
				{
					int off = i;
					while(--off >= lastOffset)
					{
						if(!Character.isLetter(line.array[off]))
							break;
					}
					off++;
					int len = i - off;
					String id = keywords.lookup(line,off,len);
					if(id != null)
					{
						if(off != lastOffset)
							addToken(off - lastOffset,null);
						addToken(len,id);
						lastOffset = i;
					}
				}
				break;
			case ':':
				backslash = false;
				if(token == null)
				{
					addToken((i+1) - lastOffset,Token.LABEL);
					lastOffset = i + 1;
				}
				break;
			case '#':
				backslash = false;
				if(cpp & token == null)
				{
					token = Token.KEYWORD2;
					addToken(i - lastOffset,null);
					addToken(length - i,Token.KEYWORD2);
					lastOffset = length;
					break loop;
				}
				break;
			case '/':
				backslash = false;
				if(token == null && length - i >= 1)
				{
					switch(line.array[i+1])
					{
					case '*':
						token = Token.COMMENT1;
						addToken(i - lastOffset,null);
						lastOffset = i;
						i++;
						break;
					case '/':
						addToken(i - lastOffset,token);
						addToken(length - i,Token.COMMENT1);
						lastOffset = length;
						break loop;
					}
				}
				break;
			case '*':
				backslash = false;
				if(token == Token.COMMENT1 && length - i >= 1)
				{
					if(length - i > 1 && line.array[i+1] == '/')
					{
						token = null;
						i++;
						addToken((i+1) - lastOffset,Token.COMMENT1);
						lastOffset = i + 1;
					}
				}
				break;
			case '"':
				if(backslash)
					backslash = false;
				else if(token == null)
				{
					token = Token.LITERAL1;
					addToken(i - lastOffset,null);
					lastOffset = i;
				}
				else if(token == Token.LITERAL1)
				{
					token = null;
					addToken((i+1) - lastOffset,Token.LITERAL1);
					lastOffset = i + 1;
				}
				break;
			case '\'':
				if(backslash)
					backslash = false;
				else if(token == null)
				{
					token = Token.LITERAL2;
					addToken(i - lastOffset,null);
					lastOffset = i;
				}
				else if(token == Token.LITERAL2)
				{
					token = null;
					addToken((i+1) - lastOffset,Token.LITERAL2);
					lastOffset = i + 1;
				}
				break;
			default:
				backslash = false;
				break;
			}
		}
		if(token == null)
		{
			int off = length;
			while(--off >= lastOffset)
			{
				if(!Character.isLetter(line.array[off]))
					break;
			}
			off++;
			int len = length - off;
			String id = keywords.lookup(line,off,len);
			if(id != null)
			{
				if(off != lastOffset)
					addToken(off - lastOffset,null);
				addToken(len,id);
				lastOffset = length;
			}
		}
		if(lastOffset != length)
			addToken(length - lastOffset,token);
		if(token == Token.KEYWORD2 && !backslash)
			token = null;
		lineInfo[lineIndex] = token;
		if(lastToken != null)
		{
			lastToken.nextValid = false;
			return firstToken;
		}
		else
			return null;
	}

	// private members
	private boolean cpp;
	private KeywordMap keywords;
}
