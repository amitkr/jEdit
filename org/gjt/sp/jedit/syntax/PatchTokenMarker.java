/*
 * PatchTokenMarker.java - DIFF patch token marker
 * Copyright (C) 1999 Slava Pestov
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA.
 */
package org.gjt.sp.jedit.syntax;

import javax.swing.text.Segment;

/**
 * Patch/diff token marker.
 *
 * @author Slava Pestov
 * @version $Id$
 */
public class PatchTokenMarker extends TokenMarker
{
	public byte markTokensImpl(byte token, Segment line, int lineIndex)
	{
		if(line.count == 0)
			return Token.NULL;
		switch(line.array[line.offset])
		{
		case '+': case '>':
			addToken(line.count,Token.KEYWORD1);
			break;
		case '-': case '<':
			addToken(line.count,Token.KEYWORD2);
			break;
		case '@': case '*':
			addToken(line.count,Token.KEYWORD3);
			break;
	        default:
			addToken(line.count,Token.NULL);
			break;
		}
		return Token.NULL;
	}
}

/*
 * ChangeLog:
 * $Log$
 * Revision 1.5  1999/06/05 00:22:58  sp
 * LGPL'd syntax package
 *
 * Revision 1.4  1999/05/22 08:33:53  sp
 * FAQ updates, mode selection tweak, patch mode update, javadoc updates, JDK 1.1.8 fix
 *
 * Revision 1.3  1999/04/19 05:38:20  sp
 * Syntax API changes
 *
 * Revision 1.2  1999/03/12 23:51:00  sp
 * Console updates, uncomment removed cos it's too buggy, cvs log tags added
 *
 */
