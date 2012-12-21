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

package de.dfki.madm.anomalydetection.operator.evaluation;

import com.rapidminer.operator.performance.PerformanceCriterion;
import com.rapidminer.tools.math.Averagable;

public class ROCPerformanceVector extends PerformanceCriterion
{
    private static final long serialVersionUID = 0x9521b86b20eb304dL;
    private String name;
    private double exampleCount;
    private double value;
    
    public ROCPerformanceVector(String name, double value) {
        this.name = "";
        exampleCount = 1.0;
        this.name = name;
        this.value = value;
    }

    public String getDescription() {
        return (new StringBuilder()).append("The estimated performance '").append(name).append("'").toString();
    }

    public double getExampleCount() {
        return exampleCount;
    }

    public double getFitness() {
        return 0.0;
    }

    public String getName() {
        return name;
    }

    public double getMikroAverage() {
        return value / exampleCount;
    }

    public double getMikroVariance() {
        return Double.NaN;
    }

    protected void buildSingleAverage(Averagable averagable) {
        ROCPerformanceVector other = (ROCPerformanceVector)averagable;
        exampleCount += other.exampleCount;
        value += other.value;
    }
}
    