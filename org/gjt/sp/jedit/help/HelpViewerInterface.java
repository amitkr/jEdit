package org.gjt.sp.jedit.help;

import java.awt.Component;
import java.beans.PropertyChangeListener;

import javax.swing.event.ChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Interface supported by all HelpViewer classes.  
 * 
 * @author ezust
 * @version $id
 */
public interface HelpViewerInterface 
{
	public void gotoURL(String url, boolean addToHistory);
	public String getShortURL();
	public String getBaseURL();
	public void setTitle(String newTitle);
	public Component getComponent();
	public void queueTOCReload();
	public void dispose();
	public void addPropertyChangeListener(PropertyChangeListener l);
}
