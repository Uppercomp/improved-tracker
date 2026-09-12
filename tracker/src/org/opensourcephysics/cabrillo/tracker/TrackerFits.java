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

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.opensourcephysics.tools.DatasetCurveFitter;
import org.opensourcephysics.tools.KnownFunction;
import org.opensourcephysics.tools.UserFunction;

/**
 * Supplies additional fit functions for the data tool's curve fitter.
 * <p>
 * These are the models physics students need when they plot kinematics with
 * drag. The most important is the terminal-velocity model: an object falling
 * under gravity with <i>quadratic</i> air drag has velocity
 * <pre>
 *   v(t) = v_term * tanh(g t / v_term)
 * </pre>
 * which is a hyperbolic tangent, so neither a straight line nor a polynomial
 * can describe it. Linear (Stokes) drag gives an exponential approach instead:
 * <pre>
 *   v(t) = v_term * (1 - exp(-t / tau))
 * </pre>
 * <p>
 * Each function uses Tracker's usual four fit parameters (a, b, c, d) so it
 * behaves exactly like the built-in fits:
 * <ul>
 * <li><b>Tanh (terminal velocity)</b>: y = a + b*tanh(c*x + d), where b is the
 * terminal velocity, c = g/v_term for quadratic drag from rest, and d is a time
 * shift for data that does not start at x = 0</li>
 * <li><b>Exp saturation (drag)</b>: y = a + b*(1 - exp(-c*x + d)), where the
 * asymptote a + b is the terminal velocity and c = 1/tau for linear drag</li>
 * </ul>
 * The functions are added to the curve fitter's list of built-in fits at
 * startup, so they appear in the fit dropdown for every track. Parameter names,
 * initial guesses and fixed values are edited with the standard fit controls,
 * and the fitted curve is drawn on the plot.
 *
 * @author Tracker
 */
public final class TrackerFits {

	/** name of the hyperbolic tangent terminal-velocity fit */
	public static final String TANH_NAME = "Tanh (terminal velocity)"; //$NON-NLS-1$

	/** name of the exponential saturation drag fit */
	public static final String EXP_NAME = "Exp saturation (drag)"; //$NON-NLS-1$

	/** the independent variable used by Tracker fits */
	private static final String VAR = "x"; //$NON-NLS-1$

	/** fit parameter names, matching the convention of Tracker's built-in fits */
	private static final String[] PARAM_NAMES = { "a", "b", "c", "d" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

	/** initial parameter guesses: offset 0, amplitude 1, rate 1, shift 0 */
	private static final double[] PARAM_VALUES = { 0, 1, 1, 0 };

	private static boolean registered;

	private TrackerFits() {
		// static utility
	}

	/**
	 * Adds the Tracker fit functions to the curve fitter's built-in list. Safe to
	 * call repeatedly and from any thread; the fits are added only once.
	 */
	public static void register() {
		if (registered) {
			return;
		}
		try {
			ArrayList<KnownFunction> fits = getDefaultFits();
			if (fits == null) {
				return;
			}
			registered = true;
			addFit(fits, TANH_NAME, "a + b*tanh(c*" + VAR + " + d)", //$NON-NLS-1$ //$NON-NLS-2$
					"Terminal velocity (quadratic drag): a + b*tanh(c*x + d)"); //$NON-NLS-1$
			addFit(fits, EXP_NAME, "a + b*(1 - exp(-c*" + VAR + " + d))", //$NON-NLS-1$ //$NON-NLS-2$
					"Terminal velocity (linear drag): a + b*(1 - exp(-c*x + d))"); //$NON-NLS-1$
		} catch (Throwable t) {
			// the extra fits are a convenience: never let them break startup
		}
	}

	/**
	 * Gets the names of the Tracker fits that have been registered.
	 *
	 * @return an array of fit names, may be empty
	 */
	public static String[] getRegisteredFitNames() {
		ArrayList<KnownFunction> fits = getDefaultFits();
		if (fits == null) {
			return new String[0];
		}
		ArrayList<String> names = new ArrayList<>();
		for (KnownFunction f : fits) {
			String name = f.getName();
			if (TANH_NAME.equals(name) || EXP_NAME.equals(name)) {
				names.add(name);
			}
		}
		return names.toArray(new String[0]);
	}

	/** @return true once the extra fits have been added */
	public static boolean isRegistered() {
		return registered;
	}

	/**
	 * Adds one fit function to the list if it is not already there.
	 *
	 * @param fits        the curve fitter's built-in fit list
	 * @param name        the fit name shown in the dropdown
	 * @param expression  the fit expression in terms of x
	 * @param description a short description of the model, may be null
	 */
	private static void addFit(ArrayList<KnownFunction> fits, String name, String expression,
			String description) {
		for (KnownFunction f : fits) {
			if (name.equals(f.getName())) {
				return; // already present
			}
		}
		UserFunction fit = new UserFunction(name);
		double[] values = new double[PARAM_NAMES.length];
		System.arraycopy(PARAM_VALUES, 0, values, 0, values.length);
		fit.setParameters(PARAM_NAMES, values);
		fit.setExpression(expression, new String[] { VAR });
		if (description != null) {
			try {
				fit.setDescription(description);
			} catch (Throwable t) {
				// description is optional
			}
		}
		fits.add(fit);
	}

	/**
	 * Gets the curve fitter's static list of built-in fits, or null if it cannot be
	 * reached.
	 *
	 * @return the list of default fits
	 */
	@SuppressWarnings("unchecked")
	private static ArrayList<KnownFunction> getDefaultFits() {
		try {
			Field f = DatasetCurveFitter.class.getDeclaredField("defaultFits"); //$NON-NLS-1$
			f.setAccessible(true);
			Object value = f.get(null);
			return (ArrayList<KnownFunction>) value;
		} catch (Throwable t) {
			return null;
		}
	}
}
