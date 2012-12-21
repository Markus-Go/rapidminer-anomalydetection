/*
 *  RapidMiner Anomaly Detection Extension
 *
 *  Copyright (C) 2009-2011 by Deutsches Forschungszentrum fuer
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
 * Author: Mennatallah Amer (mennatallah.amer@student.guc.edu.eg)
 * Responsible: Markus Goldstein (Markus.Goldstein@dfki.de)
 *
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */
package de.dfki.madm.anomalydetection.operator.nearest_neighbor_based;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.Attributes;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.table.DataRowFactory;
import com.rapidminer.example.table.DataRowReader;
import com.rapidminer.example.table.MemoryExampleTable;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.preprocessing.MaterializeDataInMemory;

import de.dfki.madm.anomalydetection.operator.AbstractAnomalyDetectionOperator;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * The abstract class for nearest neighbor based operators. It makes additional
 * pre-processing which is to group the points with the same spatial coordinates
 * together because they will have the same anomaly score for those operators
 * and it would be more efficient to remove redundancies.
 * 
 * @author Mennatallah Amer 
 * 
 */
public abstract class AbstractNearestNeighborBasedAnomalyDetectionOperator
		extends AbstractAnomalyDetectionOperator {

	
	/** The points with distinct spatial coordinates**/
	private double[][] distinctPoints;
	
	/** Each entry represents the indices of the points having the same spatial coordinate assigned to that index**/
	private LinkedList<Integer>[] mapping;


	/** The number of points assigned to that index **/
	private int[] weight;

	

	public AbstractNearestNeighborBasedAnomalyDetectionOperator(
			OperatorDescription description) {
		super(description);
	}

	@Override
	public void doWork() throws OperatorException {
		ExampleSet exampleSet = getExampleSetInput().getData(ExampleSet.class);
		this.logNote(getName());
		int type = DataRowFactory.TYPE_DOUBLE_ARRAY;
		if (exampleSet.getExampleTable() instanceof MemoryExampleTable) {
			DataRowReader reader = exampleSet.getExampleTable()
					.getDataRowReader();
			if (reader.hasNext())
				type = reader.next().getType();
		}
		ExampleSet resultSet = null;
		if (type >= 0)
			resultSet = MaterializeDataInMemory.materializeExampleSet(
					exampleSet, type);
		else
			resultSet = (ExampleSet) exampleSet.clone();
		Attributes attributes = resultSet.getAttributes();
		
		Attribute anomalyScore = initializeAnomalyScore(resultSet, attributes);
		double[][] points = initializePoints(resultSet, attributes);
		preprocessing(points, exampleSet.size());
		this.logNote("Number of distinct records "+ distinctPoints.length);
		double[] res = doWork(resultSet, attributes, distinctPoints, weight);
		storeResult(resultSet, res, anomalyScore);
		getExampleSetOutput().deliver(resultSet); 
		getOriginalOutput().deliver(exampleSet);
		

	}

	public double[] doWork(ExampleSet exampleSet, Attributes attributes,
			double[][] points, int[] weight) throws OperatorException {
		return null;
	}

	@Override
	public double[][] initializePoints(ExampleSet exampleSet,
			Attributes attributes) {
		double[][] points = new double[exampleSet.size()][attributes.size()];
		int currentExample = 0;
		
		for (Example example : exampleSet) {
			int i = 0;
			for (Attribute currentAttribute : attributes) {
				points[currentExample][i++] = example
						.getValue(currentAttribute);
			}
			currentExample++;
		}
		return points;
	}

	@SuppressWarnings("unchecked")
	public double[][] preprocessing(double[][] points, int n) {

		int distinctPointsnumber = 1;
		LinkedList<Integer>[] lists = new LinkedList[n];
		Point[] orderedPoints = new Point[n];
		for (int i = 0; i < n; i++) {
			orderedPoints[i] = new Point(i, points[i]);
		}
		Arrays.sort(orderedPoints);
		int lastIndex = 0;
		int inserIn = orderedPoints[0].index;

		lists[inserIn] = new LinkedList<Integer>();
		lists[inserIn].add(new Integer(orderedPoints[0].index));

		for (int i = 1; i < n; i++) {
			if (orderedPoints[lastIndex].compareTo(orderedPoints[i]) != 0) {
				lastIndex = i;
				inserIn = orderedPoints[i].index;
				lists[inserIn] = new LinkedList<Integer>();
				distinctPointsnumber++;
			}

			lists[inserIn].add(new Integer(orderedPoints[i].index));

		}


		distinctPoints = new double[distinctPointsnumber][];
		mapping = new LinkedList[distinctPointsnumber];
		weight = new int[distinctPointsnumber];
		int j = 0;
		for (int i = 0; i < n; i++) {
			if (lists[i] != null) {
				mapping[j] = lists[i];
				weight[j] = lists[i].size();
				distinctPoints[j++] = points[i];
				if (j == distinctPointsnumber)
					break;
			}
		}

		return distinctPoints;
	}

   @Override
public void storeResult(ExampleSet exampleSet, double[] res,
		Attribute anomalyScore) {
	for (int i = 0; i < res.length; i++) {
		for (int id : mapping[i])
			exampleSet.getExample(id).setValue(anomalyScore, res[i]);
	}
}
}
