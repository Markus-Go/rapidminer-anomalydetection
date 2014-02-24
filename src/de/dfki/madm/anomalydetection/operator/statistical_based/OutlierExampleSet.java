/*
 *  RapidMiner Anomaly Detection Extension
 *
 *  Copyright (C) 2009-2014 by Deutsches Forschungszentrum fuer
 *  Kuenstliche Intelligenz GmbH or its licensors, as applicable.
 *
 *  This is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this software. If not, see <http://www.gnu.org/licenses/.
 *
 * Author: Johann Gebhardt
 * Responsible: Markus Goldstein (Markus.Goldstein@dfki.de)
 *
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */
package de.dfki.madm.anomalydetection.operator.statistical_based;

import java.util.HashMap;
import java.util.Iterator;
import com.rapidminer.example.Attributes;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.Example;
import com.rapidminer.example.set.AbstractExampleSet;
import com.rapidminer.example.table.ExampleTable;

/** New example set type for the color coded HBOS output.
 *  Contains a regular example set and a map saving the color
 *  values of all data points.
 */
public class OutlierExampleSet extends AbstractExampleSet{
	private static final long serialVersionUID = 859614105L;
	ExampleSet data;
	HashMap<Integer, int[]> colors;
	public OutlierExampleSet(ExampleSet exampleSet,HashMap<Integer,int[]> colors){
		this.colors = colors;
		this.data = exampleSet;
	}
	@Override
	public String getName() {
		return "OutlierExampleSet";
	}
	@Override
	public Attributes getAttributes() {
		return data.getAttributes();
	}

	@Override
	public Example getExample(int arg0) {
		return data.getExample(arg0);
	}

	@Override
	public ExampleTable getExampleTable() {
		return data.getExampleTable();
	}

	@Override
	public int size() {
		return data.size();
	}
	@Override
	public OutlierExampleSet clone() {
		OutlierExampleSet result = new OutlierExampleSet((ExampleSet)data.clone(),(HashMap<Integer,int[]>)colors.clone());
		return result;
		
	}
	@Override
	public Iterator<Example> iterator() {
		return data.iterator();
	}


}
