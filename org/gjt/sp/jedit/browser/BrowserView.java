/*
 * BrowserView.java
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2000, 2001, 2002 Slava Pestov
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.gjt.sp.jedit.browser;

//{{{ Imports
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.tree.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import org.gjt.sp.jedit.io.*;
import org.gjt.sp.jedit.*;
//}}}

/**
 * VFS browser tree view.
 * @author Slava Pestov
 * @version $Id$
 */
public class BrowserView extends JPanel
{
	//{{{ BrowserView constructor
	public BrowserView(VFSBrowser browser, final boolean splitHorizontally)
	{
		this.browser = browser;
		this.splitHorizontally = splitHorizontally;

		parentModel = new DefaultListModel();
		parentDirectories = new JList(parentModel);

		parentDirectories.getSelectionModel().setSelectionMode(
			TreeSelectionModel.SINGLE_TREE_SELECTION);

		parentDirectories.setCellRenderer(new ParentDirectoryRenderer());
		parentDirectories.setVisibleRowCount(5);
		parentDirectories.addMouseListener(new MouseHandler());

		rootNode = new DefaultMutableTreeNode(null,true);
		model = new DefaultTreeModel(rootNode,true);

		tree = new BrowserJTree(model);
		tree.setCellRenderer(renderer);
		tree.setEditable(false);
		tree.addTreeExpansionListener(new TreeHandler());
		tree.putClientProperty("JTree.lineStyle", "Angled");
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.setVisibleRowCount(12);

		final JScrollPane parentScroller = new JScrollPane(parentDirectories);
		parentScroller.setMinimumSize(new Dimension(0,0));
		JScrollPane treeScroller = new JScrollPane(tree);
		treeScroller.setMinimumSize(new Dimension(0,0));
		splitPane = new JSplitPane( 
			splitHorizontally ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT,
			parentScroller,treeScroller);

		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				String prop = splitHorizontally ? "vfs.browser.horizontalSplitter" : "vfs.browser.splitter";
				int loc = jEdit.getIntegerProperty(prop,-1);
				if(loc == -1)
					loc = parentScroller.getPreferredSize().height;

				splitPane.setDividerLocation(loc);
				parentDirectories.ensureIndexIsVisible(
					parentDirectories.getModel()
					.getSize());
			}
		});

		tmpExpanded = new Hashtable();

		if(browser.isMultipleSelectionEnabled())
			tree.getSelectionModel().setSelectionMode(
				TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
		else
			tree.getSelectionModel().setSelectionMode(
				TreeSelectionModel.SINGLE_TREE_SELECTION);

		setLayout(new BorderLayout());

		add(BorderLayout.CENTER,splitPane);

		propertiesChanged();
	} //}}}

	//{{{ requestDefaultFocus() method
	public boolean requestDefaultFocus()
	{
		tree.requestFocus();
		return true;
	} //}}}

	//{{{ removeNotify() method
	public void removeNotify()
	{
		String prop = splitHorizontally ? "vfs.browser.horizontalSplitter" : "vfs.browser.splitter";
		jEdit.setIntegerProperty(prop,splitPane.getDividerLocation());

		super.removeNotify();
	} //}}}

	//{{{ getSelectedFiles() method
	public VFS.DirectoryEntry[] getSelectedFiles()
	{
		Vector selected = new Vector(tree.getSelectionCount());
		TreePath[] paths = tree.getSelectionPaths();
		if(paths == null)
			return new VFS.DirectoryEntry[0];

		for(int i = 0; i < paths.length; i++)
		{
			DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)
				paths[i].getLastPathComponent();
			Object obj = treeNode.getUserObject();
			if(obj instanceof VFS.DirectoryEntry)
				selected.addElement(obj);
		}

		VFS.DirectoryEntry[] retVal = new VFS.DirectoryEntry[selected.size()];
		selected.copyInto(retVal);
		return retVal;
	} //}}}

	//{{{ selectNone() method
	public void selectNone()
	{
		tree.setSelectionPaths(new TreePath[0]);
	} //}}}

	//{{{ directoryLoaded() method
	public void directoryLoaded(DefaultMutableTreeNode node,
		String path, Vector directory)
	{
		if(node == rootNode)
		{
			parentModel.removeAllElements();
			String parent = path;

			if(parent.length() != 1 && (parent.endsWith("/")
				|| parent.endsWith(File.separator)))
				parent = parent.substring(0,parent.length() - 1);

			for(;;)
			{
				parentModel.insertElementAt(parent,0);
				String newParent = MiscUtilities.getParentOfPath(parent);
				if(newParent.length() != 1 && (newParent.endsWith("/")
					|| newParent.endsWith(File.separator)))
					newParent = newParent.substring(0,newParent.length() - 1);

				if(newParent == null || parent.equals(newParent))
					break;
				else
					parent = newParent;
			}

			int index = parentModel.getSize() - 1;
			parentDirectories.setSelectedIndex(index);
			parentDirectories.ensureIndexIsVisible(parentModel.getSize() - 1);
		}

		node.removeAllChildren();

		Vector toExpand = new Vector();

		if(directory != null)
		{
			for(int i = 0; i < directory.size(); i++)
			{
				VFS.DirectoryEntry file = (VFS.DirectoryEntry)
					directory.elementAt(i);
				boolean allowsChildren = (file.type != VFS.DirectoryEntry.FILE);
				DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(file,allowsChildren);
				node.add(newNode);
				if(tmpExpanded.get(file.path) != null)
					toExpand.addElement(new TreePath(newNode.getPath()));
			}
		}

		// fire events
		model.reload(node);
		tree.expandPath(new TreePath(node.getPath()));

		// expand branches that were expanded before
		for(int i = 0; i < toExpand.size(); i++)
		{
			TreePath treePath = (TreePath)toExpand.elementAt(i);
			tree.expandPath(treePath);
		}

		timer.stop();
		typeSelectBuffer.setLength(0);
	} //}}}

	//{{{ updateFileView() method
	public void updateFileView()
	{
		tree.repaint();
	} //}}}

	//{{{ loadDirectory() method
	public void loadDirectory(String path)
	{
		// called by VFSBrowser.setDirectory()
		tmpExpanded.clear();
		loadDirectory(rootNode,path,false);
	} //}}}

	//{{{ maybeReloadDirectory() method
	public void maybeReloadDirectory(String path)
	{
		tmpExpanded.clear();

		// because this method is called for *every* VFS update,
		// we don't want to scan the tree all the time. So we
		// use the following algorithm to determine if the path
		// might be part of the tree:
		// - if the path starts with the browser's current directory,
		//   we do the tree scan
		// - if the browser's directory is 'favorites:' -- we have to
		//   do the tree scan, as every path can appear under the
		//   favorites list
		// - if the browser's directory is 'roots:' and path is on
		//   the local filesystem, do a tree scan
		String browserDir = browser.getDirectory();
		if(browserDir.startsWith(FavoritesVFS.PROTOCOL))
			maybeReloadDirectory(rootNode,path);
		else if(browserDir.startsWith(FileRootsVFS.PROTOCOL))
		{
			if(!MiscUtilities.isURL(path) || MiscUtilities.getProtocolOfURL(path)
				.equals("file"))
				maybeReloadDirectory(rootNode,path);
		}
		else if(path.startsWith(browserDir))
			maybeReloadDirectory(rootNode,path);
	} //}}}

	//{{{ getDefaultFocusComponent() method
	public Component getDefaultFocusComponent()
	{
		return tree;
	} //}}}

	//{{{ propertiesChanged() method
	public void propertiesChanged()
	{
		showIcons = jEdit.getBooleanProperty("vfs.browser.showIcons");
		renderer.propertiesChanged();

		tree.setRowHeight(renderer.getTreeCellRendererComponent(
			tree,new DefaultMutableTreeNode("foo"),
			false,false,false,0,false).getSize().height);

		splitPane.setBorder(null);
	} //}}}

	//{{{ Private members

	//{{{ Instance variables
	private VFSBrowser browser;

	private JSplitPane splitPane;
	private JList parentDirectories;
	private DefaultListModel parentModel;
	private JTree tree;
	private Hashtable tmpExpanded;
	private DefaultTreeModel model;
	private DefaultMutableTreeNode rootNode;
	private BrowserCommandsMenu popup;
	private boolean showIcons;
	private boolean splitHorizontally;

	private FileCellRenderer renderer = new FileCellRenderer();

	private StringBuffer typeSelectBuffer = new StringBuffer();
	private Timer timer = new Timer(0,new ClearTypeSelect());
	//}}}

	//{{{ maybeReloadDirectory() method
	private boolean maybeReloadDirectory(DefaultMutableTreeNode node, String path)
	{
		// nodes which are not expanded need not be checked
		if(!tree.isExpanded(new TreePath(node.getPath())))
			return false;

		if(node == rootNode && path.equals(browser.getDirectory()))
		{
			loadDirectory(rootNode,path,false);
			return true;
		}

		Object userObject = node.getUserObject();
		if(userObject instanceof VFS.DirectoryEntry)
		{
			VFS.DirectoryEntry file = (VFS.DirectoryEntry)userObject;

			// we don't need to do anything with files!
			if(file.type == VFS.DirectoryEntry.FILE)
				return false;

			if(path.equals(file.path))
			{
				loadDirectory(node,path,false);
				return true;
			}
		}

		if(node.getChildCount() != 0)
		{
			Enumeration children = node.children();
			while(children.hasMoreElements())
			{
				DefaultMutableTreeNode child = (DefaultMutableTreeNode)
					children.nextElement();
				if(maybeReloadDirectory(child,path))
					return true;
			}
		}

		return false;
	} //}}}

	//{{{ loadDirectory() method
	private void loadDirectory(DefaultMutableTreeNode node, String path,
		boolean showLoading)
	{
		int rowCount = tree.getRowCount();
		for(int i = 0; i < rowCount; i++)
		{
			TreePath treePath = tree.getPathForRow(i);
			if(tree.isExpanded(treePath))
			{
				DefaultMutableTreeNode _node = (DefaultMutableTreeNode)
					treePath.getLastPathComponent();
				VFS.DirectoryEntry file = ((VFS.DirectoryEntry)
					_node.getUserObject());

				tmpExpanded.put(file.path,file.path);
			}
		}

		if(node == rootNode)
		{
			parentModel.removeAllElements();
			parentModel.addElement(new LoadingPlaceholder());
		}

		if(showLoading)
		{
			node.removeAllChildren();
			node.add(new DefaultMutableTreeNode(new LoadingPlaceholder(),false));
			model.reload(node);
		}

		browser.loadDirectory(node,path,node == rootNode);
	} //}}}

	//{{{ showFilePopup() method
	private void showFilePopup(VFS.DirectoryEntry file, Point point)
	{
		popup = new BrowserCommandsMenu(browser,file);
		GUIUtilities.showPopupMenu(popup,tree,point.x+1,point.y+1);
	} //}}}

	//}}}

	//{{{ Inner classes

	//{{{ ClearTypeSelect
	class ClearTypeSelect implements ActionListener
	{
		public void actionPerformed(ActionEvent evt)
		{
			typeSelectBuffer.setLength(0);
			browser.filesSelected();
		}
	} //}}}

	//{{{ ParentDirectoryRenderer class
	class ParentDirectoryRenderer extends DefaultListCellRenderer
	{
		Font plainFont, boldFont;

		ParentDirectoryRenderer()
		{
			plainFont = UIManager.getFont("Tree.font");
			boldFont = new Font(plainFont.getName(),Font.BOLD,plainFont.getSize());
		}

		public Component getListCellRendererComponent(
			JList list,
			Object value,
			int index,
			boolean isSelected,
			boolean cellHasFocus)
		{
			super.getListCellRendererComponent(list,value,index,
				isSelected,cellHasFocus);

			ParentDirectoryRenderer.this.setBorder(new EmptyBorder(
				1,index * 10 + 1,1,1));

			if(value instanceof LoadingPlaceholder)
			{
				ParentDirectoryRenderer.this.setFont(plainFont);

				setIcon(showIcons ? FileCellRenderer.loadingIcon : null);
				setText(jEdit.getProperty("vfs.browser.tree.loading"));
			}
			else
			{
				ParentDirectoryRenderer.this.setFont(boldFont);

				setIcon(showIcons ? FileCellRenderer.openDirIcon : null);
				setText(MiscUtilities.getFileName(value.toString()));
			}

			return this;
		}
	} //}}}

	//{{{ MouseHandler class
	class MouseHandler extends MouseAdapter
	{
		public void mouseClicked(MouseEvent evt)
		{
			// ignore double clicks
			if(evt.getClickCount() % 2 == 0)
				return;

			int row = parentDirectories.locationToIndex(evt.getPoint());
			if(row != -1)
			{
				Object obj = parentModel.getElementAt(row);
				if(obj instanceof String)
				{
					browser.setDirectory((String)obj);
					requestDefaultFocus();
				}
			}
		}
	} //}}}

	//{{{ BrowserJTree class
	class BrowserJTree extends JTree
	{
		//{{{ BrowserJTree constructor
		BrowserJTree(TreeModel model)
		{
			super(model);
			ToolTipManager.sharedInstance().registerComponent(this);
		} //}}}

		//{{{ getToolTipText() method
		public final String getToolTipText(MouseEvent evt)
		{
			TreePath path = getPathForLocation(evt.getX(), evt.getY());
			if(path != null)
			{
				Rectangle cellRect = getPathBounds(path);
				if(cellRect != null && !cellRectIsVisible(cellRect))
					return path.getLastPathComponent().toString();
			}
			return null;
		} //}}}

		//{{{ getToolTipLocation() method
		public final Point getToolTipLocation(MouseEvent evt)
		{
			TreePath path = getPathForLocation(evt.getX(), evt.getY());
			if(path != null)
			{
				Rectangle cellRect = getPathBounds(path);
				if(cellRect != null && !cellRectIsVisible(cellRect))
				{
					return new Point(cellRect.x + (showIcons ? 14 : - 4),
						cellRect.y);
				}
			}
			return null;
		} //}}}

		//{{{ processKeyEvent() method
		protected void processKeyEvent(KeyEvent evt)
		{
			// could make things somewhat easier...
			// ... but KeyEventWorkaround 'output contract' will
			// change in 4.1, so not a good idea
			//evt = KeyEventWorkaround.processKeyEvent(evt);
			//if(evt == null)
			//	return;

			if(evt.getID() == KeyEvent.KEY_PRESSED)
			{
				switch(evt.getKeyCode())
				{
				case KeyEvent.VK_ENTER:
					browser.filesActivated(evt.isShiftDown(),false);
					evt.consume();
					break;
				case KeyEvent.VK_LEFT:
					String directory = browser.getDirectory();
					browser.setDirectory(MiscUtilities
						.getParentOfPath(directory));
					evt.consume();
					break;
				}
			}
			else if(evt.getID() == KeyEvent.KEY_TYPED)
			{
				if(evt.isControlDown() || evt.isAltDown()
					|| evt.isMetaDown())
				{
					return;
				}

				switch(evt.getKeyChar())
				{
				case '~':
					browser.setDirectory(System.getProperty("user.home"));
					break;
				case '/':
					browser.rootDirectory();
					break;
				case '-':
					View view = browser.getView();
					Buffer buffer = view.getBuffer();
					browser.setDirectory(MiscUtilities.getParentOfPath(
						buffer.getPath()));
					break;
				default:
					typeSelectBuffer.append(evt.getKeyChar());
					doTypeSelect(typeSelectBuffer.toString());

					timer.stop();
					timer.setInitialDelay(750);
					timer.setRepeats(false);
					timer.start();
					break;
				}

				return;
			}

			if(!evt.isConsumed())
				super.processKeyEvent(evt);
		} //}}}

		//{{{ processMouseEvent() method
		protected void processMouseEvent(MouseEvent evt)
		{
			ToolTipManager ttm = ToolTipManager.sharedInstance();

			switch(evt.getID())
			{
			//{{{ MOUSE_ENTERED...
			case MouseEvent.MOUSE_ENTERED:
				toolTipInitialDelay = ttm.getInitialDelay();
				toolTipReshowDelay = ttm.getReshowDelay();
				ttm.setInitialDelay(200);
				ttm.setReshowDelay(0);
				super.processMouseEvent(evt);
				break; //}}}
			//{{{ MOUSE_EXITED...
			case MouseEvent.MOUSE_EXITED:
				ttm.setInitialDelay(toolTipInitialDelay);
				ttm.setReshowDelay(toolTipReshowDelay);
				super.processMouseEvent(evt);
				break; //}}}
			//{{{ MOUSE_CLICKED...
			case MouseEvent.MOUSE_CLICKED:
				if((evt.getModifiers() & MouseEvent.BUTTON2_MASK) != 0)
				{
					TreePath path = getPathForLocation(evt.getX(),evt.getY());
					if(path == null)
					{
						super.processMouseEvent(evt);
						break;
					}

					if(!isPathSelected(path))
						setSelectionPath(path);

					browser.filesActivated(evt.isShiftDown(),true);
					break;
				}
				else if((evt.getModifiers() & MouseEvent.BUTTON1_MASK) != 0)
				{
					TreePath path = getPathForLocation(evt.getX(),evt.getY());
					if(path == null)
					{
						super.processMouseEvent(evt);
						break;
					}

					if(!isPathSelected(path))
						setSelectionPath(path);

					if(evt.getClickCount() == 1)
					{
						browser.filesSelected();
						super.processMouseEvent(evt);
					}
					// A double click is not only when clickCount == 2
					// because every other click can open a new directory
					if(evt.getClickCount() % 2 == 0)
					{
						// don't pass double-clicks to tree, otherwise
						// directory nodes will be expanded and we don't
						// want that
						browser.filesActivated(evt.isShiftDown(),true);
						break;
					}
				}
				else if(GUIUtilities.isPopupTrigger(evt))
					; // do nothing

				super.processMouseEvent(evt);
				break; //}}}
			//{{{ MOUSE_PRESSED...
			case MouseEvent.MOUSE_PRESSED:
				if((evt.getModifiers() & MouseEvent.BUTTON1_MASK) != 0)
				{
					if(popup != null && popup.isVisible())
						popup.setVisible(false);

					// A double click is not only when clickCount == 2
					// because every other click can open a new directory
					if(evt.getClickCount() % 2 == 0)
						break;
				}
				else if(GUIUtilities.isPopupTrigger(evt))
				{
					if(popup != null && popup.isVisible())
					{
						popup.setVisible(false);
						break;
					}

					TreePath path = getPathForLocation(evt.getX(),evt.getY());
					if(path == null)
						showFilePopup(null,evt.getPoint());
					else
					{
						setSelectionPath(path);
						browser.filesSelected();

						Object userObject = ((DefaultMutableTreeNode)path
							.getLastPathComponent()).getUserObject();
						if(userObject instanceof VFS.DirectoryEntry)
						{
							VFS.DirectoryEntry file = (VFS.DirectoryEntry)
								userObject;
							showFilePopup(file,evt.getPoint());
						}
						else
							showFilePopup(null,evt.getPoint());
					}

					break;
				}

				super.processMouseEvent(evt);
				break; //}}}
			//{{{ MOUSE_RELEASED...
			case MouseEvent.MOUSE_RELEASED:
				// A double click is not only when clickCount == 2
				// because every other click can open a new directory
				if(evt.getClickCount() % 2 != 0)
					super.processMouseEvent(evt);
				break; //}}}
			default:
				super.processMouseEvent(evt);
				break;
			}
		} //}}}

		//{{{ Private members
		private int toolTipInitialDelay = -1;
		private int toolTipReshowDelay = -1;

		//{{{ cellRectIsVisible() method
		private boolean cellRectIsVisible(Rectangle cellRect)
		{
			Rectangle vr = BrowserJTree.this.getVisibleRect();
			return vr.contains(cellRect.x,cellRect.y) &&
				vr.contains(cellRect.x + cellRect.width,
				cellRect.y + cellRect.height);
		} //}}}

		//{{{ doTypeSelect() method
		private void doTypeSelect(String str)
		{
			if(getSelectionCount() == 0)
				doTypeSelect(str,0,getRowCount());
			else
			{
				int start = getMaxSelectionRow();
				boolean retVal = doTypeSelect(str,start,getRowCount());

				if(!retVal)
				{
					// scan from selection to end failed, so
					// scan from start to selection
					doTypeSelect(str,0,start);
				}
			}
		} //}}}

		//{{{ doTypeSelect() method
		private boolean doTypeSelect(String str, int start, int end)
		{
			for(int i = start; i < end; i++)
			{
				DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)
					getPathForRow(i).getLastPathComponent();
				Object obj = treeNode.getUserObject();
				if(obj instanceof VFS.DirectoryEntry)
				{
					VFS.DirectoryEntry file = (VFS.DirectoryEntry)obj;
					if(file.name.regionMatches(true,0,str,0,str.length()))
					{
						setSelectionRow(i);
						scrollRowToVisible(i);
						return true;
					}
				}
			}

			return false;
		} //}}}

		//}}}
	} //}}}

	//{{{ TreeHandler class
	class TreeHandler implements TreeExpansionListener
	{
		//{{{ treeExpanded() method
		public void treeExpanded(TreeExpansionEvent evt)
		{
			TreePath path = evt.getPath();
			DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)
				path.getLastPathComponent();
			Object userObject = treeNode.getUserObject();
			if(userObject instanceof VFS.DirectoryEntry)
			{
				loadDirectory(treeNode,((VFS.DirectoryEntry)
					userObject).path,true);
			}
		} //}}}

		//{{{ treeCollapsed() method
		public void treeCollapsed(TreeExpansionEvent evt)
		{
			TreePath path = evt.getPath();
			DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)
				path.getLastPathComponent();
			if(treeNode.getUserObject() instanceof VFS.DirectoryEntry)
			{
				// we add the placeholder so that the node has
				// 1 child (otherwise the user won't be able to
				// expand it again)
				treeNode.removeAllChildren();
				treeNode.add(new DefaultMutableTreeNode(new LoadingPlaceholder(),false));
				model.reload(treeNode);
			}
		} //}}}
	} //}}}

	static class LoadingPlaceholder {}
	//}}}
}
