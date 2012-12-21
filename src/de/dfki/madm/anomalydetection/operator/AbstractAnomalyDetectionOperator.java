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
package de.dfki.madm.anomalydetection.operator;

import java.util.ArrayList;
import java.util.HashMap;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.Attributes;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.table.AttributeFactory;
import com.rapidminer.example.table.DataRowFactory;
import com.rapidminer.example.table.DataRowReader;
import com.rapidminer.example.table.MemoryExampleTable;
import com.rapidminer.example.table.NominalMapping;

import com.rapidminer.operator.Operator;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.operator.ports.metadata.AttributeMetaData;
import com.rapidminer.operator.ports.metadata.ExampleSetMetaData;

import com.rapidminer.operator.ports.metadata.MetaData;
import com.rapidminer.operator.ports.metadata.PassThroughRule;
import com.rapidminer.operator.preprocessing.MaterializeDataInMemory;

import com.rapidminer.tools.Ontology;

/**
 * The Abstract Anomaly Detection Operator that defines the basic input ports,
 * output ports and does the common preprocessing. 
 * 
 * @author Mennatallah Amer
 * 
 */
public abstract class AbstractAnomalyDetectionOperator extends Operator {

	/**
	 * input port
	 */
	private InputPort exampleSetInput = getInputPorts().createPort(
			"example set", ExampleSet.class);

	/**
	 * output port
	 */
	private OutputPort exampleSetOutput = getOutputPorts().createPort(
			"example set");

	/**
	 * Original output port
	 */
	private OutputPort originalOutput= getOutputPorts().createPort("original set");

	/**
	 * List of exampleSet Id
	 */
	private ArrayList<Object> ids;
	/**
	 * HashMap containing the mapping of the example Ids to the index of the
	 * example which will be used in the further processing
	 */
	private HashMap<Object, Integer> idMap;

	public AbstractAnomalyDetectionOperator(OperatorDescription description) {
		super(description);
		// Adding the outlier attribute to the meta data
		getTransformer().addRule(
				new PassThroughRule(exampleSetInput, exampleSetOutput, false) {
					@Override
					public MetaData modifyMetaData(MetaData metaData) {
						if (metaData instanceof ExampleSetMetaData) {
							ExampleSetMetaData exampleSetMetaData = (ExampleSetMetaData) metaData;
							AttributeMetaData amd = new AttributeMetaData(
									Attributes.OUTLIER_NAME, Ontology.REAL,
									Attributes.OUTLIER_NAME);
							exampleSetMetaData.addAttribute(amd);
							return exampleSetMetaData;
						} else {
							return metaData;
						}

					}
				});
		getTransformer().addPassThroughRule(exampleSetInput, originalOutput);

	}

	/**
	 * The method performs the common tasks for most anomaly detection operators
	 * so that doWork(ExampleSet exampleSet, Attributes attributes, double[][]
	 * points) is enough to be overridden in the subclasses to do the
	 * functionality of the operator
	 */
	@Override
	public void doWork() throws OperatorException {
		ExampleSet exampleSet = exampleSetInput.getData(ExampleSet.class);

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
		double[] res = doWork(resultSet, attributes, points);
		storeResult(resultSet, res, anomalyScore);
		originalOutput.deliver(exampleSet);
		exampleSetOutput.deliver(resultSet);

	}

	/**
	 * The method that should be implemented by the subclasses.
	 * 
	 * @param exampleSet
	 *            example set
	 * @param attributes
	 *            the attributes of the example set
	 * @param points
	 *            the array containing the points in the example set
	 * @return The result array that contains the anomaly score.
	 * 
	 * @throws OperatorException
	 */
	public double[] doWork(ExampleSet exampleSet, Attributes attributes,
			double[][] points) throws OperatorException {
		return null;

	}

	public InputPort getExampleSetInput() {
		return exampleSetInput;
	}

	public OutputPort getExampleSetOutput() {
		return exampleSetOutput;
	}

	public HashMap<Object, Integer> getIdMap() {
		return idMap;
	}

	public ArrayList<Object> getIds() {
		return ids;
	}

	public OutputPort getOriginalOutput() {
		return originalOutput;
	}

	/**
	 * Initializes the outlier attribute
	 * 
	 * @param exampleSet
	 * @param attributes
	 * @return anomalyScore Attribute
	 */
	public Attribute initializeAnomalyScore(ExampleSet exampleSet,
			Attributes attributes) {
		Attribute anomalyScore = AttributeFactory.createAttribute(
				Attributes.OUTLIER_NAME, Ontology.REAL);
		exampleSet.getExampleTable().addAttribute(anomalyScore);
		attributes.setOutlier(anomalyScore);
		return anomalyScore;
	}

	/**
	 * Initializes the points from the example set.
	 * 
	 * @param exampleSet
	 *            the input example set
	 * @param attributes
	 *            the attributes of the exampleSet
	 * @return points the initialized points from the exampleSet
	 */
	public double[][] initializePoints(ExampleSet exampleSet,
			Attributes attributes) {
		double[][] points = new double[exampleSet.size()][attributes.size()];
		int currentExample = 0;
		Attribute idAttribute= exampleSet.getAttributes().getId();
		
		ids = new ArrayList<Object>();
		idMap = new HashMap<Object, Integer>();
		for (Example example : exampleSet) {
			int i = 0;
			Object id;
			if(idAttribute!=null){
			if(idAttribute.isNominal()){
				NominalMapping nominalMapping = idAttribute.getMapping();
				id = nominalMapping.mapIndex((int)(example.getValue(idAttribute)));
				
			}
			else {
				id = example.getValue(idAttribute);
			}
			ids.add(id);
			idMap.put(id, currentExample);
			}
			for (Attribute currentAttribute : attributes) {

				points[currentExample][i++] = example
						.getValue(currentAttribute);
			}
			currentExample++;
		}
		return points;
	}

	public void storeResult(ExampleSet exampleSet, double[] res,
			Attribute anomalyScore) {
		int current = 0;
		for (Example example : exampleSet) {
			example.setValue(anomalyScore, res[current++]);

		}
	}

}
