/*
 * Buffer.java - jEdit buffer
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

package org.gjt.sp.jedit;

import gnu.regexp.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;
import org.gjt.sp.jedit.gui.SyntaxTextArea;
import org.gjt.sp.jedit.syntax.*;

/**
 * An in-memory copy of an open file.
 *
 * @see jEdit#openFile(View)
 * @see jEdit#openFile(View,String,String,boolean,boolean)
 * @see jEdit#closeBuffer(View)
 * @see jEdit#getBuffers()
 */
public class Buffer extends PlainDocument
implements DocumentListener, UndoableEditListener
{
	/**
	 * Size of I/O buffers.
	 */
	public static final int IOBUFSIZE = 32768;

	/**
	 * Line separator property.
	 */
	public static final String LINESEP = "lineSeparator";

	/**
	 * Finds the next instance of the search string in this buffer.
	 * The search string is obtained from the
	 * <code>search.find.value</code> property.
	 * @param view The view
	 * @param done For internal use. False if a `keep searching'
	 * dialog should be shown if no more matches have been found.
	 */
	public boolean find(View view, boolean done)
	{
		return find(view,view.getTextArea().getCaretPosition(),done);
	}

	/**
	 * Finds the next instance of the search string in this buffer.
	 * The search string is obtained from the
	 * <code>search.find.value</code> property.
	 * @param view The view
	 * @param start Location where to start the search
	 * @param done For internal use. False if a `keep searching'
	 * dialog should be shown if no more matches have been found.
	 */
	public boolean find(View view, int start, boolean done)
	{
		try
		{
			RE regexp = jEdit.getRE();
			if(regexp == null)
			{
				view.getToolkit().beep();
				return false;
			}
			String text = getText(start,getLength() - start);
			REMatch match = regexp.getMatch(text);
			if(match != null)
			{
				view.getTextArea().select(start + match
					.getStartIndex(),
					start + match.getEndIndex());
				return true;
			}
			if(done)
			{
				view.getToolkit().beep();
				return false;
			}
			int result = JOptionPane.showConfirmDialog(view,
				jEdit.getProperty("keepsearching.message"),
				jEdit.getProperty("keepsearching.title"),
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE);
			if(result == JOptionPane.YES_OPTION)
				return find(view,0,true);
		}
		catch(Exception e)
		{
			Object[] args = { e.getMessage() };
			if(args[0] == null)
				args[0] = e.toString();
			jEdit.error(view,"reerror",args);
		}
		return false;
	}

	/**
	 * Replaces the current selection with the replacement string.
	 * <p>
	 * The replacement string is obtained from the
	 * <code>search.replace.value</code> property.
	 * @param view The view
	 */
	public void replace(View view)
	{
		if(!view.getTextArea().isEditable())
		{
			view.getToolkit().beep();
			return;
		}
		try
		{
			RE regexp = jEdit.getRE();
			String replaceStr = jEdit.getProperty("search.replace.value");
			if(regexp == null)
			{
				view.getToolkit().beep();
				return;
			}
			SyntaxTextArea textArea = view.getTextArea();
			String selection = textArea.getSelectedText();
			if(selection == null)
				selection = "";
			textArea.replaceSelection(regexp.substitute(selection,
				replaceStr));
		}
		catch(Exception e)
		{
			Object[] args = { e.getMessage() };
			if(args[0] == null)
				args[0] = e.toString();
			jEdit.error(view,"reerror",args);
		}
	}

	/**
	 * Replaces all occurances of the search string with the replacement
	 * string.
	 * @param view The view
	 * @param index The index where to start the search
	 * @param length The end offset of the search
	 * @return True if the replace operation was successful, false
	 * if no matches were found
	 */
	public boolean replaceAll(View view, int index, int length)
	{
		if(!view.getTextArea().isEditable())
			return false;
		boolean found = false;
		beginCompoundEdit();
		try
		{
			RE regexp = jEdit.getRE();
			String replaceStr = jEdit.getProperty("search.replace.value");
			if(regexp == null)
			{
				endCompoundEdit();
				return false;
			}
			REMatch match;
			while((match = regexp.getMatch(getText(index,
				length - index))) != null)
			{
				int start = match.getStartIndex() + index;
				int len = match.getEndIndex() - match
					.getStartIndex();
				String str = getText(start,len);
				remove(start,len);
				String subst = regexp.substitute(str,replaceStr);
				index = start + subst.length();
				insertString(start,subst,null);
				found = true;
			}
		}
		catch(Exception e)
		{
			Object[] args = { e.getMessage() };
			if(args[0] == null)
				args[0] = e.toString();
			jEdit.error(view,"reerror",args);
		}
		endCompoundEdit();
		return found;
	}

	/**
	 * Autosaves this buffer.
	 */
	public void autosave()
	{
		if(adirty)
		{
			try
			{
				File autosaveTmp = new File(autosaveFile
					.getPath().concat("+tmp+#"));
				save(new FileOutputStream(autosaveTmp));
				autosaveTmp.renameTo(autosaveFile);
				/* XXX race alert if dirty() runs here */
				adirty = false;
			}
			catch(FileNotFoundException fnf)
			{
				/* this could happen if eg, the directory
				 * containing this file was renamed.
				 * we ignore the error then so the user
				 * isn't flooded with exceptions. */
			}
			catch(Exception e)
			{
				System.err.println("Error during autosave:");
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Prompts the user for a file to save this buffer to.
	 * @param view The view
	 */
	public boolean saveAs(View view)
	{
		JFileChooser chooser = new JFileChooser(file.getParent());
		chooser.setSelectedFile(file);
		chooser.setDialogType(JFileChooser.SAVE_DIALOG);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		int retVal = chooser.showDialog(view,null);
		if(retVal == JFileChooser.APPROVE_OPTION)
		{
			File file = chooser.getSelectedFile();
			if(file != null)
				return save(view,file.getAbsolutePath());
		}
		return false;
	}

	/**
	 * Saves this buffer to the specified path name, or the current path
	 * name if it's null.
	 * @param view The view
	 * @param path The path name to save the buffer to
	 */
	public boolean save(View view, String path)
	{
		if(path == null && newFile)
			return saveAs(view);
		URL saveUrl;
		File saveFile;
		if(path == null)
		{
			saveUrl = url;
			path = this.path;
			saveFile = file;
			// only do this check if saving to original file
			// if user is doing a `Save As' they should know
			// what they're doing anyway
			long newModTime = saveFile.lastModified();
			if(newModTime > modTime)
			{
				Object[] args = { path };
				int result = JOptionPane.showConfirmDialog(view,
					jEdit.getProperty("filechanged.message",
					args),jEdit.getProperty("filechanged.title"),
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);
				if(result != JOptionPane.YES_OPTION)
					return false;
			}
		}
		else
		{
			try
			{
				saveUrl = new URL(path);
			}
			catch(MalformedURLException mu)
			{
				saveUrl = null;
			}
			saveFile = new File(path);
		}
		backup(saveFile);
		try
		{
			OutputStream out;
			if(saveUrl != null)
			{
				URLConnection connection = saveUrl
					.openConnection();
				out = connection.getOutputStream();
			}
			else
				out = new FileOutputStream(saveFile);
			if(path.endsWith(".gz"))
				out = new GZIPOutputStream(out);
			save(out);
			url = saveUrl;
			this.path = path;
			file = saveFile;
			setPath();
			saveMarkers();
			adirty = dirty = newFile = readOnly = false;
			autosaveFile.delete();
			updateTitles();
			updateBufferMenus();
			if(mode == null)
			{
				setMode();
				view.getTextArea().repaint();
			}
			else
			{
				updateTitles();
				updateBufferMenus();
			}
			modTime = file.lastModified();
			return true;
		}
		catch(IOException io)
		{
			Object[] args = { io.toString() };
			jEdit.error(view,"ioerror",args);
		}
		catch(BadLocationException bl)
		{
		}
		return false;
	}
	
	/**
	 * Finds the previous instance of an opening bracket in the buffer.
	 * The closing bracket is needed as well to handle nested brackets
	 * properly.
	 * @param dot The starting position
	 * @param openBracket The opening bracket
	 * @param closeBracket The closing bracket
	 * @exception BadLocationException if `dot' is out of range
	 */
	 public int locateBracketBackward(int dot, char openBracket,
		char closeBracket)
		throws BadLocationException
	{
		int count;
		Element map = getDefaultRootElement();
		// check current line
		int lineNo = map.getElementIndex(dot);
		Element lineElement = map.getElement(lineNo);
		int start = lineElement.getStartOffset();
		int offset = scanBackwardLine(getText(start,dot - start),
			openBracket,closeBracket,0);
		count = -offset - 1;
		if(offset >= 0)
			return start + offset;
		// check previous lines
		for(int i = lineNo - 1; i >= 0; i--)
		{
			lineElement = map.getElement(i);
			start = lineElement.getStartOffset();
			offset = scanBackwardLine(getText(start,lineElement
				.getEndOffset() - start),openBracket,
				closeBracket,count);
			count = -offset - 1;
			if(offset >= 0)
				return start + offset;
		}
		// not found
		return -1;
	}

	/**
	 * Finds the next instance of a closing bracket in the buffer.
	 * The opening bracket is needed as well to handle nested brackets
	 * properly.
	 * @param dot The starting position
	 * @param openBracket The opening bracket
	 * @param closeBracket The closing bracket
	 * @exception BadLocationException if `dot' is out of range
	 */
	public int locateBracketForward(int dot, char openBracket,
		char closeBracket)
		throws BadLocationException
	{
		int count;
		Element map = getDefaultRootElement();
		// check current line
		int lineNo = map.getElementIndex(dot);
		Element lineElement = map.getElement(lineNo);
		int start = lineElement.getStartOffset();
		int end = lineElement.getEndOffset();
		int offset = scanForwardLine(getText(dot + 1,end - (dot + 1)),
			openBracket,closeBracket,0);
		count = -offset - 1;
		if(offset >= 0)
			return dot + offset + 1;
		// check following lines
		for(int i = lineNo + 1; i < map.getElementCount(); i++)
		{
			lineElement = map.getElement(i);
			start = lineElement.getStartOffset();
			offset = scanForwardLine(getText(start,lineElement
				.getEndOffset() - start),openBracket,
				closeBracket,count);
			count = -offset - 1;
			if(offset >= 0)
				return start + offset;
		}
		// not found
		return -1;
	}

	/**
	 * Returns the line number where the paragraph of specified
	 * location starts. Paragraphs are separated by double newlines.
	 * @param lineNo The line number
	 */
	public int locateParagraphStart(int lineNo)
	{
		Element map = getDefaultRootElement();
		for(int i = lineNo - 1; i >= 0; i--)
		{
			Element lineElement = map.getElement(i);
			if(lineElement.getEndOffset() - lineElement
				.getStartOffset() == 1)
			{
				return i;
			}
		}
		return 0;
	}

	/**
	 * Returns the line number where the paragraph of specified
	 * location ends. Paragraphs are separated by double newlines.
	 * @param lineNo The line number
	 */
	public int locateParagraphEnd(int lineNo)
	{
		Element map = getDefaultRootElement();
		int lineCount = map.getElementCount();
		for(int i = lineNo + 1; i < lineCount; i++)
		{
			Element lineElement = map.getElement(i);
			if(lineElement.getEndOffset() - lineElement
				.getStartOffset() == 1)
			{
				return i;
			}
		}
		return lineCount - 1;
	}

	/**
	 * Returns the file this buffer is editing.
	 */
	public File getFile()
	{
		return file;
	}

	/**
	 * Returns the autosave file for this buffer.
	 */
	public File getAutosaveFile()
	{
		return autosaveFile;
	}

	/**
	 * Returns the name of this buffer.
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the path name of this buffer.
	 */
	public String getPath()
	{
		return path;
	}
	
	/**
	 * Returns true if this is an untitled file, false otherwise.
	 */
	public boolean isNewFile()
	{
		return newFile;
	}
	
	/**
	 * Returns true if this file has changed since last save, false
	 * otherwise.
	 */
	public boolean isDirty()
	{
		return dirty;
	}
	
	/**
	 * Returns true if this file is read only, false otherwise.
	 */
	public boolean isReadOnly()
	{
		return readOnly;
	}
	
	/**
	 * Returns this buffer's undo manager.
	 */
	public UndoManager getUndo()
	{
		return undo;
	}

	/**
	 * Starts a compound edit that can be undone in one go.
	 */
	public void beginCompoundEdit()
	{
		if(compoundEdit == null)
			compoundEdit = new CompoundEdit();
	}

	/**
	 * Ends a compound edit.
	 */
	public void endCompoundEdit()
	{
		if(compoundEdit != null)
		{
			compoundEdit.end();
			if(compoundEdit.canUndo())
				undo.addEdit(compoundEdit);
			compoundEdit = null;
		}
	}

	/**
	 * Returns this buffer's edit mode.
	 */
	public Mode getMode()
	{
		return mode;
	}
	
	/**
	 * Returns the localised name of this buffer's edit mode.
	 */
	public String getModeName()
	{
		return jEdit.getModeName(mode);
	}
	
	/**
	 * Sets this buffer's edit mode.
	 * @param mode The mode
	 */
	public void setMode(Mode mode)
	{
		if(this.mode != null)
			this.mode.leave(this);
		this.mode = mode;
		if(mode == null)
		{
			tokenMarker = null;
		}
		else
		{
			setTokenMarker(mode.createTokenMarker());
			colors.clear();
			mode.enter(this);
		}
	}

	/**
	 * Sets this buffer's edit mode by looking at the extension,
	 * file name and first line.
	 */
	public void setMode()
	{
		String nogzName = name.substring(0,name.length() -
			(name.endsWith(".gz") ? 3 : 0));
		Mode mode = jEdit.getMode(jEdit.getProperty(
			"mode.filename.".concat(nogzName)));
		if(mode != null)
			setMode(mode);
		else
		{
			int index = nogzName.lastIndexOf('.') + 1;
			mode = jEdit.getMode(jEdit.getProperty(
				"mode.extension.".concat(nogzName
				.substring(index))));
			if(mode != null)
				setMode(mode);
			else
			{
				Element lineElement = getDefaultRootElement()
					.getElement(0);
				int start = lineElement.getStartOffset();
				try
				{
					String line = getText(start,lineElement
						.getEndOffset() - start - 1);
					setMode(jEdit.getMode(jEdit.getProperty(
						"mode.firstline." + line)));
				}
				catch(BadLocationException bl)
				{
				}
			}
		}
	}

	/**
	 * Returns the token marker for this buffer.
	 */
	public TokenMarker getTokenMarker()
	{
		if(jEdit.getSyntaxColorizing())
			return tokenMarker;
		else
			return null;
	}

	/**
	 * Sets the token marker for this buffer.
	 * @param tokenMarker the new token marker
	 */
	public void setTokenMarker(TokenMarker tokenMarker)
	{
		this.tokenMarker = tokenMarker;
		tokenizeLines();
	}

	/**
	 * Returns the colors for syntax colorizing.
	 */
	public Hashtable getColors()
	{
		return colors;
	}

	/**
	 * Returns an enumeration of set markers.
	 */
	public Enumeration getMarkers()
	{
		return markers.elements();
	}
	
	/**
	 * Adds a marker to this buffer.
	 * @param name The name of the marker
	 * @param start The start offset of the marker
	 * @param end The end offset of this marker
	 */
	public void addMarker(String name, int start, int end)
	{
		if(readOnly && !init)
			return;
		dirty = !init;
		name = name.replace(';',' ');
		Marker markerN;
		try
		{
			markerN = new Marker(name,createPosition(start),
				createPosition(end));
		}
		catch(BadLocationException bl)
		{
			return;
		}
		if(!init)
		{
			for(int i = 0; i < markers.size(); i++)
			{
				Marker marker = (Marker)markers.elementAt(i);
				if(marker.getName().equals(name))
				{
					markers.setElementAt(markerN,i);
					break;
				}
				if(marker.getStart() > start)
				{
					markers.insertElementAt(markerN,i);
					updateMarkers();
					return;
				}
			}
		}
		markers.addElement(markerN);
		if(!init)
			updateMarkers();
	}

	/**
	 * Removes the marker with the specified name.
	 * @param name The name of the marker to remove
	 */
	public void removeMarker(String name)
	{
		if(readOnly)
			return;
		dirty = !init;
		for(int i = 0; i < markers.size(); i++)
		{
			Marker marker = (Marker)markers.elementAt(i);
			if(marker.getName().equals(name))
			{
				markers.removeElementAt(i);
				updateMarkers();
				return;
			}
		}
	}
	
	/**
	 * Returns the marker with the specified name.
	 * @param name The marker name
	 */
	public Marker getMarker(String name)
	{
		Enumeration enum = getMarkers();
		while(enum.hasMoreElements())
		{
			Marker marker = (Marker)enum.nextElement();
			if(marker.getName().equals(name))
				return marker;
		}
		return null;
	}

	/**
	 * Moves the anchor to a new position.
	 * @param pos The new anchor position
	 */
	public void setAnchor(int pos)
	{
		try
		{
			anchor = createPosition(pos);
		}
		catch(BadLocationException bl)
		{
			anchor = null;
		}
	}

	/**
	 * Returns the anchor position.
	 */
	public int getAnchor()
	{
		return (anchor == null ? -1 : anchor.getOffset());
	}

	/**
	 * Returns the tab size.
	 */
	public int getTabSize()
	{
		Integer i = (Integer)getProperty(tabSizeAttribute);
		if(i != null)
			return i.intValue();
		else
			return 8;
	}

	/**
	 * Reloads the tab size setting from the properties.
	 */
	public void propertiesChanged()
	{
		// reset the color cache
		colors.clear();
	}
	
	/**
	 * Saves the caret information.
	 * @param savedSelStart The selection start
	 * @param savedSelEnd The selection end
	 */
	public void setCaretInfo(int savedSelStart, int savedSelEnd)
	{
		this.savedSelStart = savedSelStart;
		this.savedSelEnd = savedSelEnd;
	}

	/**
	 * Returns the saved selection start.
	 */
	public int getSavedSelStart()
	{
		return savedSelStart;
	}

	/**
	 * Returns the saved selection end.
	 */
	public int getSavedSelEnd()
	{
		return savedSelEnd;
	}

	// event handlers
	public void undoableEditHappened(UndoableEditEvent evt)
	{
		if(compoundEdit != null)
			compoundEdit.addEdit(evt.getEdit());
		else
			undo.addEdit(evt.getEdit());
	}

	public void insertUpdate(DocumentEvent evt)
	{
		dirty();
		if(mode instanceof DocumentListener)
			((DocumentListener)mode).insertUpdate(evt);
		if(tokenMarker == null)
			return;
		DocumentEvent.ElementChange ch = evt.getChange(
			getDefaultRootElement());
		if(ch == null)
			return;
		int line = ch.getIndex();
		Element[] children = ch.getChildrenAdded();
		if(children == null)
			return;
		for(int i = 0; i < children.length; i++)
			tokenMarker.insertLine(i + line);
	}

	public void removeUpdate(DocumentEvent evt)
	{
		dirty();
		if(mode instanceof DocumentListener)
			((DocumentListener)mode).removeUpdate(evt);
		if(tokenMarker == null)
			return;
		DocumentEvent.ElementChange ch = evt.getChange(
			getDefaultRootElement());
		if(ch == null)
			return;
		int line = ch.getIndex();
		Element[] children = ch.getChildrenRemoved();
		if(children == null)
			return;
		for(int i = 0; i < children.length; i++)
			tokenMarker.deleteLine(i + line);
	}

	public void changedUpdate(DocumentEvent evt)
	{
		dirty();
		if(mode instanceof DocumentListener)
			((DocumentListener)mode).changedUpdate(evt);
	}

	// crude hack for CloseDialog
	/**
	 * Returns a string representation of this buffer.
	 * This simply returns the path name.
	 */
	public String toString()
	{
		return path;
	}

	// package-private methods
	Buffer(URL url, String path, boolean readOnly, boolean newFile)
	{
		this.url = url;
		this.path = path;
		this.newFile = newFile;
		this.readOnly = readOnly;
		init();
	}

	// private methods
	private File file;
	private long modTime;
	private File autosaveFile;
	private File markersFile;
	private URL url;
	private URL markersUrl;
	private String name;
	private String path;
	private boolean init;
	private boolean newFile;
	private boolean adirty; /* Has file changed since last *auto* save? */
	private boolean dirty;
	private boolean readOnly;
	private boolean alreadyBackedUp;
	private Mode mode;
	private UndoManager undo;
	private CompoundEdit compoundEdit;
	private Vector markers;
	private Position anchor;
	private int savedSelStart;
	private int savedSelEnd;
	private TokenMarker tokenMarker;
	private Hashtable colors;

	private void init()
	{	
		init = true;
		setDocumentProperties(new BufferProps());
		// silly hack for backspace to work
		putProperty("i18n",Boolean.FALSE);
		undo = new UndoManager();
		markers = new Vector();
		colors = new ColorList();
		setPath();
		if(!newFile)
		{
			if(file.exists())
				this.readOnly |= !file.canWrite();
			if(autosaveFile.exists())
			{
				Object[] args = { autosaveFile.getPath() };
				jEdit.message(null,"autosaveexists",args);
			}
			load();
			loadMarkers();
		}
		String userMode = (String)getProperty("mode");
		if(userMode != null)
			setMode(jEdit.getMode(userMode));
		else
			setMode();
		addDocumentListener(this);
		addUndoableEditListener(this);
		updateMarkers();
		propertiesChanged();
		init = false;
	}
	
	private void setPath()
	{
		if(url == null)
		{
			file = new File(path);
			name = file.getName();
			markersFile = new File(file.getParent(),'.' + name
				+ ".marks");
		}
		else
		{
			name = url.getFile();
			if(name.length() == 0)
				name = url.getHost();
			file = new File(name);
			name = file.getName();
			try
			{
				markersUrl = new URL(url,'.' + new File(name)
					.getName() + ".marks");
			}
			catch(MalformedURLException mu)
			{
				markersUrl = null;
			}
		}
		// if we don't do this, the autosave of a file won't be
		// deleted after a save as
		if(autosaveFile != null)
			autosaveFile.delete();
		autosaveFile = new File(file.getParent(),'#' + name + '#');
	}
	
	private void load()
	{
		InputStream _in;
		URLConnection connection = null;
		StringBuffer sbuf = new StringBuffer();
		try
		{
			
			if(url != null)
				_in = url.openStream();
			else
				_in = new FileInputStream(file);
			if(name.endsWith(".gz"))
				_in = new GZIPInputStream(_in);
			InputStreamReader in = new InputStreamReader(_in);
			char[] buf = new char[IOBUFSIZE];
			int len; // Number of characters in buffer
			int lineCount = 0;
			boolean CRLF = false; // Windows line endings
			boolean CROnly = false; // MacOS line endings
			boolean lastWasCR = false; // Was the previous character CR?
			
			while((len = in.read(buf,0,buf.length)) != -1)
			{
				int lastLine = 0; // Offset of last line
				for(int i = 0; i < len; i++)
				{
					char c = buf[i];
					switch(c)
					{
					case '\r':
						if(lastWasCR) // \r\r, probably Mac
						{
							CROnly = true;
							CRLF = false;
						}
						else
						{
							lastWasCR = true;
						}
						sbuf.append(buf,lastLine,i -
							lastLine);
						sbuf.append('\n');
						lastLine = i + 1;
						break;
					case '\n':
						if(lastWasCR) // \r\n, probably DOS
						{
							CROnly = false;
							CRLF = true;
							lastWasCR = false;
							lastLine = i + 1;
						}
						else // Unix
						{
							CROnly = false;
							CRLF = false;
							sbuf.append(buf,lastLine,
								i - lastLine);
							sbuf.append('\n');
							lastLine = i + 1;
						}
						break;
					default:
						if(lastWasCR)
						{
							CROnly = true;
							CRLF = false;
							lastWasCR = false;
						}
						break;
					}
				}
				sbuf.append(buf,lastLine,len - lastLine);
			}
			if(CRLF)
				putProperty(LINESEP,"\r\n");
			else if(CROnly)
				putProperty(LINESEP,"\r");
			else
				putProperty(LINESEP,"\n");
			in.close();
                        if(sbuf.length() != 0 && sbuf.charAt(sbuf.length() - 1) == '\n')
				sbuf.setLength(sbuf.length() - 1);
			insertString(0,sbuf.toString(),null);
			newFile = false;
			modTime = file.lastModified();

			// One day, we should interleave this code into
			// the above loop for top hat efficency. But for
			// now, this will suffice.
			Element map = getDefaultRootElement();
			for(int i = 0; i < 10; i++)
			{
				Element lineElement = map.getElement(i);
				if(lineElement == null)
					break;
				String line = getText(lineElement.getStartOffset(),
					lineElement.getEndOffset()
					- lineElement.getStartOffset());
				processProperty(line);
			}
		}
		catch(BadLocationException bl)
		{
			bl.printStackTrace();
		}
		catch(FileNotFoundException fnf)
		{
			Object[] args = { path };
			jEdit.error(null,"notfounderror",args);
		}
		catch(IOException io)
		{
			Object[] args = { io.toString() };
			jEdit.error(null,"ioerror",args);
		}
	}

	private void processProperty(String prop)
	{
		StringBuffer buf = new StringBuffer();
		String name = null;
		boolean escape = false;
		for(int i = 0; i < prop.length(); i++)
		{
			char c = prop.charAt(i);
			switch(c)
			{
			case ':':
				if(escape)
				{
					escape = false;
					buf.append(':');
					break;
				}
				if(name != null)
				{
					String value = buf.toString();
					try
					{
						putProperty(name,new Integer(
							value));
					}
					catch(NumberFormatException nf)
					{
						putProperty(name,value);
					}
				}
				buf.setLength(0);
				break;
			case '=':
				if(escape)
				{
					escape = false;
					buf.append('=');
					break;
				}
				name = buf.toString();
				buf.setLength(0);
				break;
			case '\\':
				if(escape)
					buf.append('\\');
				escape = !escape;
				break;
			case 'n':
				if(escape)
				{	buf.append('\n');
					escape = false;
					break;
				}
			case 't':
				if(escape)
				{
					buf.append('\t');
					escape = false;
					break;
				}
			default:
				buf.append(c);
				break;
			}
		}
	}

	private void loadMarkers()
	{
		try
		{
			InputStream in;
			if(url != null)
				in = markersUrl.openStream();
			else
				in = new FileInputStream(markersFile);
			StringBuffer buf = new StringBuffer();
			int c;
			boolean eof = false;
			String name = null;
			int start = -1;
			int end = -1;
			for(;;)
			{
				if(eof)
					break;
				switch(c = in.read())
				{
				case -1:
					eof = true;
				case ';': case '\n': case '\r':
					if(buf.length() == 0)
						continue;
					String str = buf.toString();
					buf.setLength(0);
					if(name == null)
						name = str;
					else if(start == -1)
					{
						try
						{
							start = Integer
								.parseInt(str);
						}
						catch(NumberFormatException nf)
						{
							System.err.println(
								"Invalid"
								+ " start: "
								+ str);
							start = 0;
						}
					}
					else if(end == -1)
					{
						try
						{
							end = Integer
								.parseInt(str);
						}
						catch(NumberFormatException nf)
						{
							System.err.println(
								"Invalid"
								+ " end: "
								+ str);
							end = 0;
						}
						addMarker(name,start,end);
						name = null;
						start = -1;
						end = -1;
					}
					break;
				default:
					buf.append((char)c);
					break;
				}
			}
			in.close();
		}
		catch(Exception e)
		{
		}
	}

	private void save(OutputStream _out)
		throws IOException, BadLocationException
	{
		BufferedWriter out = new BufferedWriter(
			new OutputStreamWriter(_out),IOBUFSIZE);
		String newline = (String)getProperty(LINESEP);
		if(newline == null)
			newline = System.getProperty("line.separator");
		Element map = getDefaultRootElement();
		for(int i = 0; i < map.getElementCount(); i++)
		{
			Element line = map.getElement(i);
			int start = line.getStartOffset();
			out.write(getText(start,line.getEndOffset() - start
				- 1));
			out.write(newline);
		}
		out.close();
	}
	
	private void saveMarkers()
		throws IOException
	{
		if(markers.isEmpty())
		{
			markersFile.delete();
			return;
		}
		OutputStream out;
		if(url != null)
		{
			URLConnection connection = markersUrl.openConnection();
			out = connection.getOutputStream();
		}
		else
			out = new FileOutputStream(markersFile);
		Writer o = new OutputStreamWriter(out);
		Enumeration enum = getMarkers();
		while(enum.hasMoreElements())
		{
			Marker marker = (Marker)enum.nextElement();
			o.write(marker.getName());
			o.write(';');
			o.write(String.valueOf(marker.getStart()));
			o.write(';');
			o.write(String.valueOf(marker.getEnd()));
			o.write('\n');
		}
		o.close();
	}

	private void backup(File file)
	{
		if(alreadyBackedUp)
			return;
		alreadyBackedUp = true;
		int backups;
		try
		{
			backups = Integer.parseInt(jEdit.getProperty(
				"backups"));
		}
		catch(NumberFormatException nf)
		{
			backups = 1;
		}
		if(backups == 0)
			return;
		File backup = null;
		for(int i = backups; i > 0; i--)
		{
			backup = new File(path + (backups == 1 ?
				"~" : "~" + i + "~"));
			if(backup.exists())
			{
				if(i == backups)
					backup.delete();
				else
					backup.renameTo(new File(path + "~"
						+ (i + 1) + "~"));
			}
		}
		file.renameTo(backup);
	}

	// the return value is as follows:
	// >= 0: offset in line where bracket was found
	// < 0: -1 - count
	private int scanBackwardLine(String line, char openBracket,
		char closeBracket, int count)
	{
		for(int i = line.length() - 1; i >= 0; i--)
		{
			char c = line.charAt(i);
			if(c == closeBracket)
				count++;
			else if(c == openBracket)
			{
				if(--count < 0)
					return i;
			}
		}
		return -1 - count;
	}

	// the return value is as follows:
	// >= 0: offset in line where bracket was found
	// < 0: -1 - count
	private int scanForwardLine(String line, char openBracket,
		char closeBracket, int count)
	{
		for(int i = 0; i < line.length(); i++)
		{
			char c = line.charAt(i);
			if(c == openBracket)
				count++;
			else if(c == closeBracket)
			{
				if(--count < 0)
					return i;
			}
		}
		return -1 - count;
	}

	private void updateTitles()
	{
		Enumeration enum = jEdit.getViews();
		while(enum.hasMoreElements())
		{
			View view = (View)enum.nextElement();
			if(view.getBuffer() == this)
				view.updateTitle();
		}
	}

	private void updateBufferMenus()
	{
		Enumeration enum = jEdit.getViews();
		while(enum.hasMoreElements())
		{
			((View)enum.nextElement()).updateBuffersMenu();
		}
	}

	private void updateMarkers()
	{
		Enumeration enum = jEdit.getViews();
		while(enum.hasMoreElements())
		{
			View view = (View)enum.nextElement();
			view.updateBuffersMenu();
			if(view.getBuffer() == this)
			{
				view.updateTitle();
				view.updateMarkerMenus();
			}
		}
	}

	private void dirty()
	{
		if(!((dirty && adirty) || readOnly))
		{
			adirty = dirty = !init;
			updateTitles();
			updateBufferMenus();
		}
	}

	private void tokenizeLines()
	{
		if(tokenMarker == null)
			return;
		Segment lineSegment = new Segment();
		Element map = getDefaultRootElement();
		int lines = map.getElementCount();
		try
		{
			for(int i = 0; i < lines; i++)
			{
				Element lineElement = map.getElement(i);
				int start = lineElement.getStartOffset();
				getText(start,lineElement.getEndOffset()
					- start - 1,lineSegment);
				tokenMarker.markTokens(lineSegment,i);
			}
		}
		catch(BadLocationException bl)
		{
		}
	}

	private class BufferProps extends Hashtable
	{
		public Object get(Object key)
		{
			// First try the buffer-local properties
			Object o = super.get(key);
			if(o != null)
				return o;

			// Now try mode.<mode>.<property>
			String value = null;
			if(mode != null)
			{
				String clazz = mode.getClass().getName();
				value = jEdit.getProperty("mode."
					+ clazz.substring(clazz
					.lastIndexOf('.')+1) + "." + key);
			}

			// Now try buffer.<property>
			if(value == null)
			{
				value = jEdit.getProperty("buffer." + key);
				if(value == null)
					return null;
			}

			// Try returning it as an integer first
			try
			{
				return new Integer(value);
			}
			catch(NumberFormatException nf)
			{
				return value;
			}
		}
	}

	private class ColorList extends Hashtable
	{
		public Object get(Object key)
		{
			Object o = super.get(key);
			if(o != null)
				return o;
			String clazz = mode.getClass().getName();
			String value = jEdit.getProperty("mode." + clazz
				.substring(clazz.lastIndexOf('.')+1)
					+ ".colors." + key);
			if(value == null)
			{
				value = jEdit.getProperty("buffer.colors."
					+ key);
				if(value == null)
					return null;
			}
			Color color = jEdit.parseColor(value);
			if(color == null)
				return null;
			put(key,color);
			return color;
		}
	}
}
