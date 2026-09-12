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

import org.opensourcephysics.controls.OSPLog;
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
 * <b>Why the parameters are not a, b, c, d.</b> These fits were originally
 * written in the same generic four-parameter form as the built-in fits,
 * <pre>
 *   y = a + b*tanh(c*x + d)
 * </pre>
 * That form is <i>degenerate</i> on v-t data measured in seconds: the whole
 * data set spans the tanh argument 0..~1.5, where tanh is nearly linear, so
 * <ol>
 * <li>the drawn curve looks like a straight line, and</li>
 * <li>a and b are not separately identified. Fitting real v-t data converges to
 * a ~ -2175 and b ~ +2176 with a completely different initial guess reaching
 * a ~ -2239, b ~ +2240, all with the same RMS to five decimals. The number a
 * student would read off as the terminal velocity is therefore meaningless.</li>
 * </ol>
 * Using the physical parameterisation instead,
 * <pre>
 *   y = A*tanh((x - t0)/tau)
 * </pre>
 * removes the degeneracy: every initial guess converges to the same A, tau and
 * t0, the fitted values are the quantities the experiment is measuring, and
 * <ul>
 * <li><b>Tanh (terminal velocity)</b>: A = v_term (m/s), tau = v_term/g (s), so
 * g = A/tau, and t0 is the release time (s)</li>
 * <li><b>Exp saturation (drag)</b>: A = v_term, tau = m/k, t0 the release time</li>
 * </ul>
 * Tracker's default initial guesses suit data in SI units, which is what the
 * data tool exports, so A~1, tau~0.2, t0~0 converge whether the plotted
 * quantity is velocity or position.
 * <p>
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

	/** physical parameter names: asymptote, time constant, release time */
	private static final String[] PARAM_NAMES = { "A", "tau", "t0" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	/** human-readable parameter descriptions shown by the fit controls */
	private static final String[] PARAM_DESCRIPTIONS = {
			"Terminal velocity (m/s)", //$NON-NLS-1$
			"Time constant (s): v_t/g for quadratic drag, m/k for linear drag", //$NON-NLS-1$
			"Release time (s): the time at which the motion starts" }; //$NON-NLS-1$

	/**
	 * Initial guesses in SI units. A ~ 1 m/s and tau ~ 0.2 s match a light object
	 * reaching terminal velocity in a fraction of a second, and t0 ~ 0 assumes the
	 * data starts at the release. Crucially these are well inside the basin of the
	 * correct solution, unlike the generic form's defaults.
	 */
	private static final double[] PARAM_VALUES = { 1, 0.2, 0 };

	private static boolean registered;

	private TrackerFits() {
		// static utility
	}

	/**
	 * Adds the Tracker fit functions to the curve fitter's built-in list.
	 * <p>
	 * This is called both from the startup thread and from the event dispatch
	 * thread and it mutates the shared static {@code DatasetCurveFitter.defaultFits}
	 * list, so it is synchronized. The registered flag is set only after both fits
	 * are in: setting it first would leave the fits permanently missing while
	 * {@link #isRegistered()} still reported success.
	 */
	public static synchronized void register() {
		if (registered) {
			return;
		}
		try {
			ArrayList<KnownFunction> fits = getDefaultFits();
			if (fits == null) {
				return;
			}
			addFit(fits, TANH_NAME, "A*tanh((" + VAR + " - t0)/tau)", //$NON-NLS-1$ //$NON-NLS-2$
					"Quadratic drag, v(t) = v_t*tanh((t - t0)/tau). " //$NON-NLS-1$
							+ "A is the terminal velocity, tau = v_t/g so g = A/tau."); //$NON-NLS-1$
			addFit(fits, EXP_NAME, "A*(1 - exp(-(" + VAR + " - t0)/tau))", //$NON-NLS-1$ //$NON-NLS-2$
					"Linear (Stokes) drag, v(t) = v_t*(1 - exp(-(t - t0)/tau)). " //$NON-NLS-1$
							+ "A is the terminal velocity, tau = m/k."); //$NON-NLS-1$
			registered = true;
		} catch (Throwable t) {
			// the extra fits are a convenience and must never break startup, but the
			// reason is still worth recording
			OSPLog.warning("unable to register the drag fit functions: " + t); //$NON-NLS-1$
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
		// ORDER MATTERS, and getting it wrong is silent. setParameters must come
		// FIRST: it registers the parameter names (and their descriptions) with the
		// function, and setExpression then substitutes those names and compiles the
		// expression. Called the other way round, setExpression returns false
		// WITHOUT throwing, the expression never compiles, and evaluate() returns
		// 0 for every x. A fit drawn from that is a flat line at zero - and when a
		// fitter is handed such a function it appears to produce a straight line.
		UserFunction params = fit;
		double[] values = new double[PARAM_NAMES.length];
		System.arraycopy(PARAM_VALUES, 0, values, 0, values.length);
		params.setParameters(PARAM_NAMES, values, PARAM_DESCRIPTIONS);
		if (!fit.setExpression(expression, new String[] { VAR })) {
			OSPLog.warning("the fit expression would not compile: " + expression); //$NON-NLS-1$
			return;
		}
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
