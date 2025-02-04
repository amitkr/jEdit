/*
 * ChunkCache.java - Intermediate layer between token lists from a TokenMarker
 * and what you see on screen
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

package org.gjt.sp.jedit.textarea;

//{{{ Imports
import javax.swing.text.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.*;
import java.util.ArrayList;
import org.gjt.sp.jedit.syntax.*;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.util.Log;
//}}}

/**
 * A "chunk" is a run of text with a specified font style and color. This class
 * contains various static methods for manipulating chunks and chunk lists. It
 * also has a number of package-private instance methods used internally by the
 * text area for painting text.
 *
 * @author Slava Pestov
 * @version $Id$
 */
public class ChunkCache
{
	//{{{ lineToChunkList() method
	/**
	 * Converts a line of text into one or more chunk lists. There will be
	 * one chunk list if soft wrap is disabled, more than one otherwise.
	 * @param seg The segment containing line text
	 * @param tokens The line's syntax tokens
	 * @param styles The styles to highlight the line with
	 * @param fontRenderContext Text transform, anti-alias, fractional font
	 * metrics
	 * @param e Used for calculating tab offsets
	 * @param wrapMargin The wrap margin width, in pixels. 0 disables
	 * @param out All resulting chunk lists will be appended to this list
	 * @since jEdit 4.0pre4
	 */
	public static void lineToChunkList(Segment seg, Token tokens,
		SyntaxStyle[] styles, FontRenderContext fontRenderContext,
		TabExpander e, float wrapMargin, java.util.List out)
	{
		// SILLY: allow for anti-aliased characters' "fuzz"
		if(wrapMargin != 0.0f)
			wrapMargin += 2.0f;

		float x = 0.0f;
		float endX = 0.0f;
		boolean seenNonWhiteSpace = false;
		boolean addedNonWhiteSpace = false;
		boolean lastWasSpace = false;
		float firstNonWhiteSpace = 0.0f;

		Chunk first = null;
		Chunk current = null;
		Chunk end = null;

		int tokenListOffset = 0;

		while(tokens.id != Token.END)
		{
			int flushIndex = tokenListOffset;

			for(int i = tokenListOffset; i < tokenListOffset + tokens.length; i++)
			{
				char ch = seg.array[seg.offset + i];

				if(ch == '\t' || (ch == ' ' && wrapMargin != 0.0f))
				{
					/* Create chunk with all text from
					 * last position up to here, and wrap
					 * if necessary. */
					//{{{
					if(i != flushIndex)
					{
						Chunk newChunk = new Chunk(
							tokens.id,seg,flushIndex,
							i,styles,fontRenderContext);
						if(current != null)
							current.next = newChunk;
						current = newChunk;

						x += newChunk.width;
						lastWasSpace = false;
						seenNonWhiteSpace = true;
					}

					if(end != null
						&& !lastWasSpace
						&& addedNonWhiteSpace
						&& wrapMargin != 0
						&& x > wrapMargin)
					{
						if(first != null)
							out.add(first);
						first = new Chunk(firstNonWhiteSpace,end.offset + 1);
						first.next = end.next;
						end.next = null;

						x = x + firstNonWhiteSpace - endX;
					}

					if(first == null)
						first = current;
					//}}}

					//{{{ Create ' ' chunk
					if(ch == ' ')
					{
						Chunk newChunk = new Chunk(
							tokens.id,seg,i,i + 1,
							styles,fontRenderContext);
						if(first == null)
							first = current = newChunk;
						else
						{
							current.next = newChunk;
							current = newChunk;
						}

						x += current.width;
					} //}}}
					//{{{ Create '\t' chunk
					else if(ch == '\t')
					{
						Chunk newChunk = new Chunk(
							tokens.id,seg,i,i,
							styles,fontRenderContext);
						if(first == null)
							first = current = newChunk;
						else
						{
							current.next = newChunk;
							current = newChunk;
						}

						float newX = e.nextTabStop(x,i + tokenListOffset);
						current.width = newX - x;
						x = newX;
						current.length = 1;
					} //}}}

					if(first == null)
						first = current;

					end = current;
					endX = x;

					if(flushIndex != i + 1)
						flushIndex = i + 1;

					lastWasSpace = true;
					addedNonWhiteSpace = seenNonWhiteSpace;

					if(!seenNonWhiteSpace)
						firstNonWhiteSpace = x;
				}
				else if(i == tokenListOffset + tokens.length - 1)
				{
					if(flushIndex != i + 1)
					{
						Chunk newChunk = new Chunk(
							tokens.id,seg,flushIndex,
							i + 1,styles,fontRenderContext);
						if(current != null)
							current.next = newChunk;
						current = newChunk;

						x += newChunk.width;
						seenNonWhiteSpace = true;
						lastWasSpace = false;
					}

					if(i == seg.count - 1 && wrapMargin != 0
						&& x > wrapMargin
						&& addedNonWhiteSpace
						&& end != null)
					{
						if(first != null)
							out.add(first);
						first = new Chunk(firstNonWhiteSpace,end.offset + 1);
						first.next = end.next;
						end.next = null;

						x = x + firstNonWhiteSpace - endX;
					}

					if(first == null)
						first = current;

					addedNonWhiteSpace = seenNonWhiteSpace;
				}
			}

			tokenListOffset += tokens.length;
			tokens = tokens.next;
		}

		if(first != null)
			out.add(first);
	} //}}}

	//{{{ paintChunkList() method
	/**
	 * Paints a chunk list.
	 * @param chunks The chunk list
	 * @param gfx The graphics context
	 * @param x The x co-ordinate
	 * @param y The y co-ordinate
	 * @param width The width of the painting area, used for a token
	 * background color hack
	 * @param background The background color of the painting area,
	 * used for background color hack
	 * @return The width of the painted text
	 * @since jEdit 4.0pre6
	 */
	public static float paintChunkList(Chunk chunks, Graphics2D gfx,
		float x, float y, Color background, boolean glyphVector)
	{
		FontMetrics forBackground = gfx.getFontMetrics();

		float _x = 0.0f;

		Chunk first = chunks;

		Font lastFont = null;
		Color lastColor = null;

		while(chunks != null)
		{
			if(!chunks.inaccessable)
			{
				Font font = chunks.style.getFont();
				Color bgColor = chunks.style.getBackgroundColor();
				if(bgColor != null)
				{
					float x2 = _x + chunks.width;

					// Workaround for bug in Graphics2D in
					// JDK1.4 under Windows; calling
					// setPaintMode() does not reset
					// graphics mode.
					Graphics2D xorGfx = (Graphics2D)gfx.create();
					xorGfx.setXORMode(background);
					xorGfx.setColor(bgColor);

					xorGfx.fill(new Rectangle2D.Float(
						x + _x,y - forBackground.getAscent(),
						x2 - _x,forBackground.getHeight()));

					xorGfx.dispose();
				}

				if(chunks.str != null)
				{
					gfx.setFont(font);
					gfx.setColor(chunks.style.getForegroundColor());

					if(glyphVector)
						gfx.drawGlyphVector(chunks.text,x + _x,y);
					else
						gfx.drawString(chunks.str,x + _x,y);

					// Useful for debugging purposes
					//gfx.draw(new Rectangle2D.Float(x + chunks.x,y - 10,
					//	chunks.width,10));
				}
			}

			_x += chunks.width;
			chunks = chunks.next;
		}

		return _x;
	} //}}}

	//{{{ offsetToX() method
	/**
	 * Converts an offset in a chunk list into an x co-ordinate.
	 * @param chunks The chunk list
	 * @param offset The offset
	 * @since jEdit 4.0pre4
	 */
	public static float offsetToX(Chunk chunks, int offset)
	{
		if(chunks != null && offset < chunks.offset)
		{
			throw new ArrayIndexOutOfBoundsException(offset + " < "
				+ chunks.offset);
		}

		float x = 0.0f;

		while(chunks != null)
		{
			if(!chunks.inaccessable && offset < chunks.offset + chunks.length)
			{
				if(chunks.text == null)
					break;
				else
				{
					return x + chunks.positions[
						(offset - chunks.offset) * 2];
				}
			}

			x += chunks.width;
			chunks = chunks.next;
		}

		return x;
	} //}}}

	//{{{ xToOffset() method
	/**
	 * Converts an x co-ordinate in a chunk list into an offset.
	 * @param chunks The chunk list
	 * @param x The x co-ordinate
	 * @param round Round up to next letter if past the middle of a letter?
	 * @return The offset within the line, or -1 if the x co-ordinate is too
	 * far to the right
	 * @since jEdit 4.0pre4
	 */
	public static int xToOffset(Chunk chunks, float x, boolean round)
	{
		float _x = 0.0f;

		while(chunks != null)
		{
			if(!chunks.inaccessable && x < _x + chunks.width)
			{
				if(chunks.text == null)
				{
					if(round && _x + chunks.width - x < x - _x)
						return chunks.offset + chunks.length;
					else
						return chunks.offset;
				}
				else
				{
					float xInChunk = x - _x;

					for(int i = 0; i < chunks.length; i++)
					{
						float glyphX = chunks.positions[i*2];
						float nextX = (i == chunks.length - 1
							? chunks.width
							: chunks.positions[i*2+2]);

						if(nextX > xInChunk)
						{
							if(!round || nextX - xInChunk > xInChunk - glyphX)
								return chunks.offset + i;
							else
								return chunks.offset + i + 1;
						}
					}
				}
			}

			_x += chunks.width;
			chunks = chunks.next;
		}

		return -1;
	} //}}}

	//{{{ Chunk class
	/**
	 * A linked-list useful for painting syntax highlighted text and
	 * calculating offsets.
	 * @since jEdit 4.0pre4
	 */
	public static class Chunk
	{
		// should xToOffset() ignore this chunk?
		public boolean inaccessable;

		public float width;
		public SyntaxStyle style;
		public int offset;
		public int length;
		public String str;
		public GlyphVector text;
		public float[] positions;

		public Chunk next;

		Chunk(float width, int offset)
		{
			inaccessable = true;
			this.width = width;
			this.offset = offset;
		}

		Chunk(int tokenType, Segment seg, int offset, int end,
			SyntaxStyle[] styles, FontRenderContext fontRenderContext)
		{
			style = styles[tokenType];

			if(offset != end)
			{
				length = end - offset;
				str = new String(seg.array,seg.offset + offset,length);

				text = style.getFont().createGlyphVector(
					fontRenderContext,str);
				width = (float)text.getLogicalBounds().getWidth();
				positions = text.getGlyphPositions(0,length,null);
			}

			this.offset = offset;
		}
	} //}}}

	//{{{ ChunkCache constructor
	ChunkCache(JEditTextArea textArea)
	{
		this.textArea = textArea;
		out = new ArrayList();
	} //}}}

	//{{{ getMaxHorizontalScrollWidth() method
	int getMaxHorizontalScrollWidth()
	{
		int max = 0;
		for(int i = 0; i < lineInfo.length; i++)
		{
			LineInfo info = lineInfo[i];
			if(info.chunksValid && info.width > max)
				max = info.width;
		}
		return max;
	} //}}}

	//{{{ getScreenLineOfOffset() method
	int getScreenLineOfOffset(int line, int offset)
	{
		if(line < textArea.getFirstPhysicalLine())
		{
			return -1;
		}
		else if(line > textArea.getLastPhysicalLine())
		{
			return -1;
		}
		else if(!textArea.softWrap)
		{
			return textArea.physicalToVirtual(line)
				- textArea.getFirstLine();
		}
		else
		{
			int screenLine;

			if(line == lastScreenLineP)
			{
				LineInfo last = lineInfo[lastScreenLine];

				if(offset >= last.offset
					&& offset < last.offset + last.length)
				{
					updateChunksUpTo(lastScreenLine);
					return lastScreenLine;
				}
			}

			screenLine = -1;

			// Find the screen line containing this offset
			for(int i = 0; i < lineInfo.length; i++)
			{
				updateChunksUpTo(i);

				LineInfo info = getLineInfo(i);
				if(info.physicalLine > line)
				{
					// line is invisible?
					if(i == 0)
						screenLine = 0;
					else
						screenLine = i - 1;
					break;
				}
				else if(info.physicalLine == line)
				{
					if(offset >= info.offset
						&& offset < info.offset + info.length)
					{
						screenLine = i;
						break;
					}
				}
			}

			if(screenLine == -1)
				return -1;
			else
			{
				lastScreenLineP = line;
				lastScreenLine = screenLine;

				return screenLine;
			}
		}
	} //}}}

	//{{{ recalculateVisibleLines() method
	void recalculateVisibleLines()
	{
		lineInfo = new LineInfo[textArea.getVisibleLines() + 1];
		for(int i = 0; i < lineInfo.length; i++)
			lineInfo[i] = new LineInfo();

		lastScreenLine = lastScreenLineP = -1;
	} //}}}

	//{{{ setFirstLine() method
	void setFirstLine(int firstLine)
	{
		if(textArea.softWrap || Math.abs(firstLine - this.firstLine) >= lineInfo.length)
		{
			for(int i = 0; i < lineInfo.length; i++)
			{
				lineInfo[i].chunksValid = false;
			}
		}
		else if(firstLine > this.firstLine)
		{
			System.arraycopy(lineInfo,firstLine - this.firstLine,
				lineInfo,0,lineInfo.length - firstLine
				+ this.firstLine);

			for(int i = lineInfo.length - firstLine
				+ this.firstLine; i < lineInfo.length; i++)
			{
				lineInfo[i] = new LineInfo();
			}
		}
		else if(this.firstLine > firstLine)
		{
			System.arraycopy(lineInfo,0,lineInfo,this.firstLine - firstLine,
				lineInfo.length - this.firstLine + firstLine);

			for(int i = 0; i < this.firstLine - firstLine; i++)
			{
				lineInfo[i] = new LineInfo();
			}
		}

		lastScreenLine = lastScreenLineP = -1;
		this.firstLine = firstLine;
	} //}}}

	//{{{ invalidateAll() method
	void invalidateAll()
	{
		for(int i = 0; i < lineInfo.length; i++)
		{
			lineInfo[i].chunksValid = false;
		}

		lastScreenLine = lastScreenLineP = -1;
	} //}}}

	//{{{ invalidateChunksFrom() method
	void invalidateChunksFrom(int screenLine)
	{
		for(int i = screenLine; i < lineInfo.length; i++)
		{
			lineInfo[i].chunksValid = false;
		}

		lastScreenLine = lastScreenLineP = -1;
	} //}}}

	//{{{ invalidateChunksFromPhys() method
	void invalidateChunksFromPhys(int physicalLine)
	{
		for(int i = 0; i < lineInfo.length; i++)
		{
			if(lineInfo[i].physicalLine >= physicalLine)
			{
				invalidateChunksFrom(i);
				break;
			}
		}
	} //}}}

	//{{{ lineToChunkList() method
	void lineToChunkList(int physicalLine, ArrayList out)
	{
		TextAreaPainter painter = textArea.getPainter();
		Buffer buffer = textArea.getBuffer();

		buffer.getLineText(physicalLine,textArea.lineSegment);

		lineToChunkList(textArea.lineSegment,
			buffer.markTokens(physicalLine).getFirstToken(),
			painter.getStyles(),painter.getFontRenderContext(),
			painter,textArea.softWrap
			? textArea.wrapMargin
			: 0.0f,out);
	} //}}}

	//{{{ updateChunksUpTo() method
	void updateChunksUpTo(int lastScreenLine)
	{
		if(!textArea.softWrap)
			return;

		if(lineInfo[lastScreenLine].chunksValid)
			return;

		int firstScreenLine = 0;

		for(int i = lastScreenLine; i >= 0; i--)
		{
			if(lineInfo[i].chunksValid)
			{
				firstScreenLine = i + 1;
				break;
			}
		}

		int physicalLine;

		if(firstScreenLine == 0)
		{
			physicalLine = textArea.getFirstPhysicalLine();
		}
		else
		{
			int prevPhysLine = lineInfo[
				firstScreenLine - 1]
				.physicalLine;
			if(prevPhysLine == -1)
				physicalLine = -1;
			else
			{
				physicalLine = textArea
					.getFoldVisibilityManager()
					.getNextVisibleLine(prevPhysLine);
			}
		}

		// Note that we rely on the fact that when a physical line is
		// invalidated, all screen lines/subregions of that line are
		// invalidated as well. See below comment for code that tries
		// to uphold this assumption.

		out.clear();

		int offset = 0;
		int length = 0;

		for(int i = firstScreenLine; i <= lastScreenLine; i++)
		{
			LineInfo info = lineInfo[i];

			Chunk chunks;

			if(out.size() == 0)
			{
				if(physicalLine != -1 && i != firstScreenLine)
				{
					physicalLine = textArea.getFoldVisibilityManager()
						.getNextVisibleLine(physicalLine);
				}

				if(physicalLine == -1)
				{
					info.chunks = null;
					info.chunksValid = true;
					info.physicalLine = -1;
					continue;
				}

				lineToChunkList(physicalLine,out);

				info.firstSubregion = true;

				if(out.size() == 0)
				{
					chunks = null;
					offset = 0;
					length = 1;
				}
				else
				{
					chunks = (Chunk)out.get(0);
					out.remove(0);
					offset = 0;
					if(out.size() != 0)
						length = ((Chunk)out.get(0)).offset - offset;
					else
						length = textArea.getLineLength(physicalLine) - offset + 1;
				}
			}
			else
			{
				info.firstSubregion = false;

				chunks = (Chunk)out.get(0);
				out.remove(0);
				offset = chunks.offset;
				if(out.size() != 0)
					length = ((Chunk)out.get(0)).offset - offset;
				else
					length = textArea.getLineLength(physicalLine) - offset + 1;
			}

			boolean lastSubregion = (out.size() == 0);

			if(i == lastScreenLine
				&& lastScreenLine != lineInfo.length - 1)
			{
				/* If this line has become longer or shorter
				 * (in which case the new physical line number
				 * is different from the cached one) we need to:
				 * - continue updating past the last line
				 * - advise the text area to repaint
				 * On the other hand, if the line wraps beyond
				 * lastScreenLine, we need to keep updating the
				 * chunk list to ensure proper alignment of
				 * invalidation flags (see start of method) */
				if(info.physicalLine != physicalLine
					|| info.lastSubregion != lastSubregion)
				{
					lastScreenLine++;
					needFullRepaint = true;
				}
				else if(out.size() != 0)
					lastScreenLine++;
			}

			info.physicalLine = physicalLine;
			info.lastSubregion = lastSubregion;
			info.offset = offset;
			info.length = length;
			info.chunks = chunks;
			info.chunksValid = true;
		}
	} //}}}

	//{{{ getLineInfo() method
	LineInfo getLineInfo(int screenLine)
	{
		LineInfo info = lineInfo[screenLine];

		if(textArea.softWrap)
		{
			if(!info.chunksValid)
				Log.log(Log.ERROR,this,"Not up-to-date: " + screenLine);
			return info;
		}
		else
		{
			if(!info.chunksValid)
			{
				int virtLine = screenLine + firstLine;
				if(virtLine >= textArea.getVirtualLineCount())
				{
					info.chunks = null;
					info.chunksValid = true;
					info.physicalLine = -1;
				}
				else
				{
					info.physicalLine = textArea.getFoldVisibilityManager()
						.virtualToPhysical(virtLine);

					out.clear();

					lineToChunkList(info.physicalLine,out);

					info.firstSubregion = true;
					info.lastSubregion = true;
					info.offset = 0;
					info.length = textArea.getLineLength(info.physicalLine) + 1;
					info.chunks = (out.size() == 0 ? null :
						(Chunk)out.get(0));
					info.chunksValid = true;
				}
			}

			return info;
		}
	} //}}}

	//{{{ getLineInfosForPhysicalLine() method
	public LineInfo[] getLineInfosForPhysicalLine(int physicalLine)
	{
		out.clear();
		lineToChunkList(physicalLine,out);

		if(out.size() == 0)
			out.add(null);

		LineInfo[] returnValue = new LineInfo[out.size()];

		for(int i = 0; i < out.size(); i++)
		{
			Chunk chunks = (Chunk)out.get(i);
			LineInfo info = new LineInfo();
			info.physicalLine = physicalLine;
			if(i == 0)
			{
				info.firstSubregion = true;
				info.offset = 0;
			}
			else
				info.offset = chunks.offset;

			if(i == out.size() - 1)
			{
				info.lastSubregion = true;
				info.length = textArea.getLineLength(physicalLine)
					- info.offset + 1;
			}
			else
			{
				info.length = ((Chunk)out.get(i + 1)).offset
					- info.offset;
			}

			info.chunksValid = true;
			info.chunks = chunks;

			returnValue[i] = info;
		}

		return returnValue;
	} //}}}

	//{{{ needFullRepaint() method
	/**
	 * The needFullRepaint variable becomes true when the number of screen
	 * lines in a physical line changes.
	 */
	boolean needFullRepaint()
	{
		boolean retVal = needFullRepaint;
		needFullRepaint = false;
		return retVal;
	} //}}}

	//{{{ getLineInfoBackwardsCompatibility() method
	LineInfo getLineInfoBackwardsCompatibility(int physicalLineIndex)
	{
		LineInfo info = new LineInfo();

		out.clear();
		Buffer buffer = textArea.getBuffer();
		buffer.getLineText(physicalLineIndex,textArea.lineSegment);

		TextAreaPainter painter = textArea.getPainter();
		lineToChunkList(textArea.lineSegment,
			buffer.markTokens(physicalLineIndex).getFirstToken(),
			painter.getStyles(),painter.getFontRenderContext(),
			painter,0.0f,out);

		if(out.size() == 0)
			info.chunks = null;
		else
			info.chunks = (Chunk)out.get(0);

		info.physicalLine = physicalLineIndex;
		info.chunksValid = true;

		return info;
	} //}}}

	//{{{ Private members
	private JEditTextArea textArea;
	private int firstLine;
	private LineInfo[] lineInfo;
	private ArrayList out;

	private int lastScreenLineP;
	private int lastScreenLine;

	private boolean needFullRepaint;
	//}}}

	//{{{ LineInfo class
	static class LineInfo
	{
		int physicalLine;
		int offset;
		int length;
		boolean firstSubregion;
		boolean lastSubregion;
		boolean chunksValid;
		Chunk chunks;
		int width;
	} //}}}
}
