/*
 * BufferPrintable.java - Printable implementation
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2001 Slava Pestov
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

package org.gjt.sp.jedit.print;

//{{{ Imports
import javax.swing.text.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.print.*;
import java.awt.*;
import java.util.*;
import org.gjt.sp.jedit.syntax.*;
import org.gjt.sp.jedit.textarea.*;
import org.gjt.sp.jedit.*;
//}}}

class BufferPrintable implements Printable
{
	//{{{ BufferPrintable constructor
	BufferPrintable(Buffer buffer, Font font, boolean header, boolean footer,
		boolean lineNumbers, boolean color)
	{
		this.buffer = buffer;
		this.font = font;
		this.header = header;
		this.footer = footer;
		this.lineNumbers = lineNumbers;

		styles = GUIUtilities.loadStyles(jEdit.getProperty("print.font"),
			jEdit.getIntegerProperty("print.fontsize",10),color);
		styles[Token.NULL] = new SyntaxStyle(textColor,null,font);

		lineList = new ArrayList();
	} //}}}

	//{{{ print() method
	public int print(Graphics _gfx, PageFormat pageFormat, int pageIndex)
		throws PrinterException
	{
		if(pageIndex > currentPage + 1)
		{
			for(int i = currentPage; i < pageIndex; i++)
			{
				printPage(_gfx,pageFormat,i,true);
			}

			currentPage = pageIndex - 1;
		}

		if(pageIndex == currentPage + 1)
		{
			if(end)
				return NO_SUCH_PAGE;

			currentPageStart = currentPhysicalLine;
			currentPage = pageIndex;
		}
		else if(pageIndex == currentPage)
		{
			currentPhysicalLine = currentPageStart;
		}

		printPage(_gfx,pageFormat,pageIndex,true);

		return PAGE_EXISTS;
	} //}}}

	//{{{ Private members

	//{{{ Static variables
	private static Color headerColor = Color.lightGray;
	private static Color headerTextColor = Color.black;
	private static Color footerColor = Color.lightGray;
	private static Color footerTextColor = Color.black;
	private static Color lineNumberColor = Color.gray;
	private static Color textColor = Color.black;
	//}}}

	//{{{ Instance variables
	private Buffer buffer;
	private Font font;
	private SyntaxStyle[] styles;
	private boolean header;
	private boolean footer;
	private boolean lineNumbers;

	private int currentPage;
	private int currentPageStart;
	private int currentPhysicalLine;
	private boolean end;

	private LineMetrics lm;
	private ArrayList lineList;
	//}}}

	//{{{ printPage() method
	private void printPage(Graphics _gfx, PageFormat pageFormat, int pageIndex,
		boolean actuallyPaint)
	{
		Graphics2D gfx = (Graphics2D)_gfx;
		gfx.setFont(font);

		double pageX = pageFormat.getImageableX();
		double pageY = pageFormat.getImageableY();
		double pageWidth = pageFormat.getImageableWidth();
		double pageHeight = pageFormat.getImageableHeight();

		if(header)
		{
			double headerHeight = paintHeader(gfx,pageX,pageY,pageWidth,
				actuallyPaint);
			pageY += headerHeight * 2;
			pageHeight -= headerHeight * 2;
		}

		if(footer)
		{
			double footerHeight = paintFooter(gfx,pageX,pageY,pageWidth,
				pageHeight,pageIndex,actuallyPaint);
			pageHeight -= footerHeight * 2;
		}

		FontRenderContext frc = gfx.getFontRenderContext();

		double lineNumberWidth;

		//{{{ determine line number width
		if(lineNumbers)
		{
			// the +1's ensure that 99 gets 3 digits, 103 gets 4 digits,
			// and so on.
			int lineNumberDigits = (int)Math.ceil(Math.log(buffer.getLineCount() + 1)
				/ Math.log(10)) + 1;

			// now that we know how many chars there are, get the width.
			char[] chars = new char[lineNumberDigits];
			for(int i = 0; i < chars.length; i++)
				chars[i] = ' ';
			lineNumberWidth = font.getStringBounds(chars,
				0,lineNumberDigits,frc).getWidth();
		}
		else
			lineNumberWidth = 0.0;
		//}}}

		//{{{ calculate tab size
		int tabSize = jEdit.getIntegerProperty("print.tabSize",8);
		char[] chars = new char[tabSize];
		for(int i = 0; i < chars.length; i++)
			chars[i] = ' ';
		double tabWidth = font.getStringBounds(chars,
			0,tabSize,frc).getWidth();
		PrintTabExpander e = new PrintTabExpander(tabWidth);
		//}}}

		Segment seg = new Segment();
		double y = 0.0;

		lm = font.getLineMetrics("gGyYX",frc);

print_loop:	for(;;)
		{
			if(currentPhysicalLine == buffer.getLineCount())
			{
				end = true;
				break print_loop;
			}

			lineList.clear();

			buffer.getLineText(currentPhysicalLine,seg);
			Token tokens = buffer.markTokens(currentPhysicalLine)
				.getFirstToken();
			ChunkCache.lineToChunkList(seg,tokens,styles,frc,
				e,(float)(pageWidth - lineNumberWidth),
				lineList);
			if(lineList.size() == 0)
				lineList.add(null);

			if(y + (lm.getHeight() * lineList.size()) >= pageHeight)
				break print_loop;

			if(lineNumbers && actuallyPaint)
			{
				gfx.setFont(font);
				gfx.setColor(lineNumberColor);
				gfx.drawString(String.valueOf(currentPhysicalLine + 1),
					(float)pageX,(float)(pageY + y));
			}

			for(int i = 0; i < lineList.size(); i++)
			{
				ChunkCache.Chunk chunks = (ChunkCache.Chunk)lineList.get(i);
				if(chunks != null && actuallyPaint)
				{
					ChunkCache.paintChunkList(chunks,gfx,
						(float)(pageX + lineNumberWidth),
						(float)(pageY + y),
						Color.white,false);
				}
				y += lm.getHeight();
			}

			currentPhysicalLine++;
		}
	} //}}}

	//{{{ paintHeader() method
	private double paintHeader(Graphics2D gfx, double pageX, double pageY,
		double pageWidth, boolean actuallyPaint)
	{
		String headerText = jEdit.getProperty("print.headerText",
			new String[] { buffer.getPath() });
		FontRenderContext frc = gfx.getFontRenderContext();
		lm = font.getLineMetrics(headerText,frc);

		Rectangle2D bounds = font.getStringBounds(headerText,frc);
		Rectangle2D headerBounds = new Rectangle2D.Double(
			pageX,pageY,pageWidth,bounds.getHeight());

		if(actuallyPaint)
		{
			gfx.setColor(headerColor);
			gfx.fill(headerBounds);
			gfx.setColor(headerTextColor);
			gfx.drawString(headerText,
				(float)(pageX + (pageWidth - bounds.getWidth()) / 2),
				(float)(pageY + lm.getAscent()));
		}

		return headerBounds.getHeight();
	}
	//}}}

	//{{{ paintFooter() method
	private double paintFooter(Graphics2D gfx, double pageX, double pageY,
		double pageWidth, double pageHeight, int pageIndex,
		boolean actuallyPaint)
	{
		String footerText = jEdit.getProperty("print.footerText",
			new Object[] { new Date(), new Integer(pageIndex + 1) });
		FontRenderContext frc = gfx.getFontRenderContext();
		lm = font.getLineMetrics(footerText,frc);

		Rectangle2D bounds = font.getStringBounds(footerText,frc);
		Rectangle2D footerBounds = new Rectangle2D.Double(
			pageX,pageY + pageHeight - bounds.getHeight(),
			pageWidth,bounds.getHeight());

		if(actuallyPaint)
		{
			gfx.setColor(footerColor);
			gfx.fill(footerBounds);
			gfx.setColor(footerTextColor);
			gfx.drawString(footerText,
				(float)(pageX + (pageWidth - bounds.getWidth()) / 2),
				(float)(pageY + pageHeight - bounds.getHeight()
				+ lm.getAscent()));
		}

		return footerBounds.getHeight();
	} //}}}

	//}}}

	//{{{ PrintTabExpander class
	static class PrintTabExpander implements TabExpander
	{
		private double tabWidth;

		//{{{ PrintTabExpander constructor
		public PrintTabExpander(double tabWidth)
		{
			this.tabWidth = tabWidth;
		} //}}}

		//{{{ nextTabStop() method
		public float nextTabStop(float x, int tabOffset)
		{
			int ntabs = (int)((x + 1) / tabWidth);
			return (float)((ntabs + 1) * tabWidth);
		} //}}}
	} //}}}
}
