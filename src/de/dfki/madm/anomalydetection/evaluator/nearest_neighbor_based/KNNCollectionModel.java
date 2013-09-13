/*
 *  RapidMiner Anomaly Detection Extension
 *
 *  Copyright (C) 2009-2013 by Deutsches Forschungszentrum fuer
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
package de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based;

import com.rapidminer.operator.AbstractModel;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.tools.math.similarity.DistanceMeasure;
/**
 * 
 * This class is used to save the knnCollection as a RapidMiner model.
 * 
 * @author Johann Gebhardt
 * 
 */
public class KNNCollectionModel extends AbstractModel{
	/**
	 *  Change this if the object changes
	 */
	private static final long serialVersionUID = -695692136502022L;
	/** the saved knnCollection*/
	KNNCollection knnCollection;
	/** the distanceMeasure used to create the model*/
	public DistanceMeasure measure;
	/** returns the knnCollection*/
	public KNNCollection get(){
		return this.knnCollection;
	}
	public KNNCollectionModel(ExampleSet trainingExampleSet, KNNCollection col,DistanceMeasure measure){
		super(trainingExampleSet);
		this.knnCollection = col;
		this.measure = measure;
	}
	public ExampleSet apply(ExampleSet tmp){
		return tmp;
		
	}
	@Override
	public String toString() {
		return getName() + " model with k = " +knnCollection.getK();
	}

}
