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
package de.dfki.madm.anomalydetection.operator.cluster_based;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.Attributes;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.table.DataRowFactory;
import com.rapidminer.example.table.DataRowReader;
import com.rapidminer.example.table.MemoryExampleTable;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.clustering.Centroid;
import com.rapidminer.operator.clustering.CentroidClusterModel;
import com.rapidminer.operator.clustering.Cluster;
import com.rapidminer.operator.clustering.ClusterModel;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.operator.ports.metadata.DistanceMeasurePrecondition;
import com.rapidminer.operator.preprocessing.MaterializeDataInMemory;
import com.rapidminer.parameter.ParameterType;

import com.rapidminer.tools.math.similarity.DistanceMeasureHelper;
import com.rapidminer.tools.math.similarity.DistanceMeasures;

import de.dfki.madm.anomalydetection.operator.AbstractAnomalyDetectionOperator;

/**
 * The Abstract clustering based anomaly detection operator defines basic
 * features and behavior for clustered based outlier detection operators.
 * 
 * @author Mennatallah Amer
 * 
 */

public abstract class AbstractClusteringAnomalyDetectionOperator extends
		AbstractAnomalyDetectionOperator {
	/** The cluster model input port. **/
	private InputPort clusterModelInput = getInputPorts().createPort(
			"cluster Model", ClusterModel.class);
	
	private OutputPort clusterModelOutput= getOutputPorts().createPort("cluster model");
	
	private DistanceMeasureHelper measureHelper = new DistanceMeasureHelper(
			this);

	/** contains the mapping between the points and the cluster they belong to. **/
	private int[] belongsToCluster;

	/** The centroids of the clusters **/
	private double[][] centriods;

	/** The size of the clusters **/
	private int[] clusterSize;

	public AbstractClusteringAnomalyDetectionOperator(
			OperatorDescription description) {
		super(description);
		getExampleSetInput().addPrecondition(
				new DistanceMeasurePrecondition(getExampleSetInput(), this));
		InputPort exampleSetInput= getInputPorts().getPortByName("example set");
		getInputPorts().renamePort(
				exampleSetInput, "clustered set");
		getInputPorts().removePort(exampleSetInput);
		getInputPorts().addPort(exampleSetInput);
		OutputPort originalOutPut = getOutputPorts().getPortByName("original set");
		getOutputPorts().removePort(originalOutPut);
		OutputPort exampleSetOutputPort= getOutputPorts().getPortByName("example set");
		getOutputPorts().renamePort(exampleSetOutputPort, "clustered set");
		getOutputPorts().removePort(exampleSetOutputPort);
		getOutputPorts().addPort(exampleSetOutputPort);
		getOutputPorts().addPort(originalOutPut);
		getTransformer().addPassThroughRule(clusterModelInput, clusterModelOutput);
		
		
	}
	
	

	public int[] getBelongsToCluster() {
		return belongsToCluster;
	}

	public double[][] getCentriods() {
		return centriods;
	}

	public int[] getClusterSize() {
		return clusterSize;
	}

	public DistanceMeasureHelper getMeasureHelper() {
		return measureHelper;
	}


	@Override
	public void doWork() throws OperatorException {
		ExampleSet exampleSet = getExampleSetInput().getData(ExampleSet.class);

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
		preprocessing(exampleSet, attributes, points);
		double[] res = doWork(resultSet, attributes, points);
		storeResult(resultSet, res, anomalyScore);
		getOriginalOutput().deliver(exampleSet);
		getExampleSetOutput().deliver(resultSet);
		clusterModelOutput.deliver(clusterModelInput.getData(ClusterModel.class));
		
		
	}
	

	
	/**
	 * Initializes the instance variables.
	 * 
	 * @param exampleSet
	 * @param attributes
	 * @param points
	 * @throws OperatorException
	 */
	public void preprocessing(ExampleSet exampleSet, Attributes attributes,
			double[][] points) throws OperatorException {
		
		ClusterModel model = clusterModelInput.getData(ClusterModel.class);
		Object[] clusters = model.getClusters().toArray();
		int numberOfClusters = clusters.length;
		
		clusterSize = new int[numberOfClusters ];
		int n = points.length;
		this.logNote("cluster number ="+ numberOfClusters);
		int attributeSize = points[0].length;
		belongsToCluster = new int[n];
		Arrays.fill(belongsToCluster, -1);
		centriods = new double[numberOfClusters][attributeSize];
		
		HashMap<Object, Integer> idMap = getIdMap();
		for (int i = 0; i < numberOfClusters ; i++) {
			clusterSize[i] = ((Cluster) clusters[i]).getNumberOfExamples();
			Collection<Object> exampleIds = ((Cluster) clusters[i])
					.getExampleIds();
			for (Object id : exampleIds) {
				if(!idMap.containsKey(id)){
					// Id present in the cluster model and not in the clustered set 
					throw new OperatorException("Incompatible Ids between the cluster model and clustered set.");
				}
				int mapping = idMap.get(id);
				belongsToCluster[mapping] = i;
				for (int j = 0; j < attributeSize; j++)
					centriods[i][j] += points[mapping][j];

			}
			for (int j = 0; j < attributeSize; j++)
				centriods[i][j] /= clusterSize[i];
			

		}
		if(model instanceof CentroidClusterModel)
		{
			List<Centroid> cent= ((CentroidClusterModel) model).getCentroids();
			for (int i=0; i< numberOfClusters; i++)
				centriods[i]= cent.get(i).getCentroid(); 
		}
		for (int i=0; i< n ; i++){
			if(belongsToCluster[i]==-1){
				double id=exampleSet.getExample(i).getId();
				if(idMap.containsKey(id)){
					int idMapped = idMap.get(id);
					for (int l=0; l< points[i].length; l++)
					{
						if(points[i][l]!= points[idMapped][l])
							throw new OperatorException("Incompatible Ids between the cluster model and  the clustered set. The clustered set might contain duplicate ids.");
					}
					belongsToCluster[i]= belongsToCluster[idMapped];
				}
				else throw new OperatorException("Incompatible Ids between the cluster model and  the clustered set. The clustered set might contain duplicate ids.");
				
				}
		}
		

	}
	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types= super.getParameterTypes();
		List<ParameterType> distancetypes = DistanceMeasures.getParameterTypes(this);
		types.addAll(distancetypes);
		return types;
	}
	
}
