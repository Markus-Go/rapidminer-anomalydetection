/*
 * RapidMiner Anomaly Detection Extension
 * 
 * Copyright (C) 2009-2012 by Deutsches Forschungszentrum fuer Kuenstliche
 * Intelligenz GmbH or its licensors, as applicable.
 * 
 * This is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this software. If not, see <http://www.gnu.org/licenses/.
 * 
 * Author: Markus Goldstein
 * Responsible: Markus Goldstein (Markus.Goldstein@dfki.de)
 * 
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */

package de.dfki.madm.anomalydetection.evaluator;

import com.rapidminer.operator.OperatorException;

public interface Evaluator{
	public double[] evaluate() throws OperatorException;
}
