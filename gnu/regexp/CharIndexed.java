/*
 *  gnu/regexp/CharIndexed.java
 *  Copyright (C) 1998-2001 Wes Biggs
 *
 *  This library is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published
 *  by the Free Software Foundation; either version 2.1 of the License, or
 *  (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package gnu.regexp;

/**
 * Defines the interface used internally so that different types of source
 * text can be accessed in the same way.  Built-in concrete classes provide
 * support for String, StringBuffer, InputStream and char[] types.
 * A class that is CharIndexed supports the notion of a cursor within a
 * block of text.  The cursor must be able to be advanced via the move()
 * method.  The charAt() method returns the character at the cursor position
 * plus a given offset.
 */
public interface CharIndexed {
    /**
     * Defines a constant (0xFFFF was somewhat arbitrarily chosen)
     * that can be returned by the charAt() function indicating that
     * the specified index is out of range.
     */
    public static final char OUT_OF_BOUNDS = '\uFFFF';

    /**
     * Returns the character at the given offset past the current cursor
     * position in the input.  The index of the current position is zero.
     */
    public char charAt(int index);

    /**
     * Shifts the input buffer by a given number of positions.  Returns
     * true if the new cursor position is valid.
     */
    public boolean move(int index);

    /**
     * Returns true if the most recent move() operation placed the cursor
     * position at a valid position in the input.
     */
    public boolean isValid();
}
