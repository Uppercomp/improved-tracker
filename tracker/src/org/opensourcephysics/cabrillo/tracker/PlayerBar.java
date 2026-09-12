/*
 * The tracker package defines a set of video/image analysis tools
 * built on the Open Source Physics framework by Wolfgang Christian.
 *
 * Copyright (c) 2026 Douglas Brown, Wolfgang Christian, Robert M. Hanson
 *
 * Tracker is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tracker is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Tracker; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston MA 02111-1307 USA
 * or view the license online at <http://www.gnu.org/copyleft/gpl.html>
 *
 * For additional Tracker information and documentation, please see
 * <https://opensourcephysics.github.io/tracker-website/>.
 */
package org.opensourcephysics.cabrillo.tracker;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import javax.swing.JSlider;
import javax.swing.JToolBar;

/**
 * A toolbar that stretches its media player component to fill the full width of
 * the bar and lays the player's own toolbar out itself, so the frame slider
 * absorbs all the leftover width.
 * <p>
 * Two things make the stock frame slider far too short:
 * <ol>
 * <li>the video player is a self-contained component whose preferred width is
 * only about 420 px, so the toolbar may leave most of the bar empty;</li>
 * <li>the default toolbar layout sizes the slider from the slider's own
 * preferred width and leaves the remaining space unused.</li>
 * </ol>
 * Laying the player's inner toolbar out here (instead of patching bounds after
 * the look &amp; feel has laid them out, which it immediately undoes) lets the
 * slider span the window, as users expect.
 *
 * @author Tracker
 */
@SuppressWarnings("serial")
class PlayerBar extends JToolBar {

	/** the width given to a stretched slider when the exact width is unknown */
	private static final int DEFAULT_SLIDER_WIDTH = 720;

	/** the component stretched to the toolbar width, or null for normal behavior */
	private Component stretchy;

	PlayerBar() {
		super();
		setFloatable(false);
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				// the bar width changed: re-run our layout so the slider follows
				doLayout();
			}
		});
	}

	/**
	 * Sets the component that should fill the toolbar width.
	 *
	 * @param c the component (typically the VideoPlayer)
	 */
	void setStretchyComponent(Component c) {
		stretchy = c;
	}

	/**
	 * Gets the component that is stretched to fill this bar.
	 *
	 * @return the stretched component, or null
	 */
	Component getStretchyComponent() {
		return stretchy;
	}

	@Override
	public void doLayout() {
		if (stretchy == null || stretchy.getParent() != this) {
			super.doLayout();
			return;
		}
		Insets insets = getInsets();
		// The player is sized to the bar width and NOT to its own preferred width:
		// a preferred width that keeps growing (the player caches the widest layout
		// it has ever seen) would otherwise push the frame slider outside the
		// visible area, which looks exactly like a very short slider.
		int w = Math.max(1, getWidth() - insets.left - insets.right);
		int h = Math.max(stretchy.getPreferredSize().height, getHeight() - insets.top - insets.bottom);
		stretchy.setBounds(insets.left, insets.top, w, h);
		if (stretchy instanceof Container) {
			Container c = (Container) stretchy;
			c.doLayout();
			layoutInnerToolBars(c);
		}
	}

	/**
	 * Recursively lays out the toolbars inside the player so sliders consume the
	 * horizontal space the other components do not use.
	 *
	 * @param container the player component
	 */
	private void layoutInnerToolBars(Container container) {
		for (Component child : container.getComponents()) {
			if (!(child instanceof Container)) {
				continue;
			}
			if (child instanceof JToolBar) {
				layoutToolBar((JToolBar) child);
			}
			layoutInnerToolBars((Container) child);
		}
	}

	/**
	 * Lays out one toolbar row: fixed components keep their preferred width while
	 * sliders share whatever space remains. The slider's preferred width is also
	 * updated, because the installed toolbar layout manager rebuilds the row from
	 * preferred sizes on later layout passes.
	 *
	 * @param bar the toolbar to lay out
	 */
	private void layoutToolBar(JToolBar bar) {
		if (!bar.isVisible()) {
			return;
		}
		Insets insets = bar.getInsets();
		int avail = bar.getWidth() - insets.left - insets.right;
		if (avail <= 0) {
			return;
		}
		int height = bar.getHeight() - insets.top - insets.bottom;
		Component[] kids = bar.getComponents();
		ArrayList<JSlider> sliders = new ArrayList<>();
		int fixed = 0;
		for (Component c : kids) {
			if (!c.isVisible()) {
				continue;
			}
			if (c instanceof JSlider) {
				sliders.add((JSlider) c);
			} else {
				fixed += c.getPreferredSize().width;
			}
		}
		if (sliders.isEmpty()) {
			bar.doLayout(); // nothing to stretch: defer to the look & feel
			return;
		}
		int available = avail - fixed;
		int sliderWidth = Math.max(DEFAULT_SLIDER_WIDTH, available / sliders.size());
		for (JSlider s : sliders) {
			Dimension sp = s.getPreferredSize();
			// keep preferred/minimum consistent with the target width
			s.setPreferredSize(new Dimension(sliderWidth, sp.height));
			s.setMinimumSize(new Dimension(Math.min(80, sliderWidth), sp.height));
			s.setMaximumSize(new Dimension(Integer.MAX_VALUE, sp.height));
		}
		// lay the row out explicitly: fixed components first, then the sliders
		int x = insets.left;
		for (Component c : kids) {
			if (!c.isVisible()) {
				continue;
			}
			Dimension pref = c.getPreferredSize();
			int cw = Math.min(pref.width, Math.max(0, avail - (x - insets.left)));
			if (c instanceof JSlider) {
				cw = Math.max(40, avail - (x - insets.left) - trailingWidth(kids, c));
			}
			int ch = Math.min(pref.height, height);
			int cy = insets.top + Math.max(0, (height - ch) / 2);
			c.setBounds(x, cy, cw, ch);
			x += cw;
		}
	}

	/** total preferred width of the visible components after the given one */
	private static int trailingWidth(Component[] kids, Component target) {
		int total = 0;
		boolean after = false;
		for (Component c : kids) {
			if (c == target) {
				after = true;
				continue;
			}
			if (after && c.isVisible()) {
				total += c.getPreferredSize().width;
			}
		}
		return total;
	}

	@Override
	public Dimension getPreferredSize() {
		Dimension d = super.getPreferredSize();
		if (stretchy == null || stretchy.getParent() != this) {
			return d;
		}
		Dimension pd = stretchy.getPreferredSize();
		Insets insets = getInsets();
		return new Dimension(d.width, Math.max(d.height, pd.height + insets.top + insets.bottom));
	}

	@Override
	public Dimension getMinimumSize() {
		Dimension d = super.getMinimumSize();
		if (stretchy == null || stretchy.getParent() != this) {
			return d;
		}
		Dimension pd = stretchy.getMinimumSize();
		Insets insets = getInsets();
		return new Dimension(d.width, Math.max(d.height, pd.height + insets.top + insets.bottom));
	}

	@Override
	public void addNotify() {
		super.addNotify();
		// the bar may be reparented (TViewChooser moves it when maximizing a view)
		if (stretchy != null) {
			revalidate();
		}
	}
}
