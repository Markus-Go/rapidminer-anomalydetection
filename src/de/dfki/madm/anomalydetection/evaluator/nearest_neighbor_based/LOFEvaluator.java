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
package de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based;

import java.util.LinkedList;

import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.tools.math.similarity.DistanceMeasure;

/**
 * This class is where the LOF algorithm is implemented.
 * 
 * @author Mennatallah Amer
 * 
 */
public class LOFEvaluator extends KNNEvaluator {
	public KNNCollection savedCollection;
	private int minK;
	public LOFEvaluator(int minK, KNNCollection knnCollection,
			DistanceMeasure measure, boolean parallel, int numberOfthreads, Operator logger) {
		super(knnCollection, false, measure, parallel, numberOfthreads, logger);
		this.minK = minK;

	}public LOFEvaluator(int minK, KNNCollection knnCollection,
			DistanceMeasure measure, boolean parallel, int numberOfthreads, Operator logger,int n , int k , boolean newCollection
			) {
		super(knnCollection, false, measure, parallel, numberOfthreads, logger,n,k,newCollection);
		this.minK = minK;
		this.newCollection = newCollection;
		
	}
	/**
	 * The method is overridden to avoid the extra unnecessary work done
	 */
	@Override
	protected void setAnomalyScore(int i, double[] neighBorDistanceSoFar,
			int[] neighBorIndiciesSoFar, int numberOfNeighbors) {

	}

	@Override
	public double[] evaluate() {
		super.evaluate();
	
		double[] lof = lof(); 
		return lof;
	}
	
	@Override
	public double[] reEvaluate(int step) {
		
		getKnnCollection().shrink(step-1);
		minK= getKnnCollection().getK();
		double[] lof = lof(); 
		return lof;
	}
	
	
	private double [] lof(){
		double [] lof = new double[getN()];
		double [] lrd= new double[getN()];
		
		int [] weight = getKnnCollection().getWeight();
		int[][] neighborIndices = getKnnCollection().getNeighBorIndiciesSoFar();
		double [][] neighborDistance = getKnnCollection().getNeighBorDistanceSoFar();	
		LinkedList<Integer>[] kdistNeighbors = getKnnCollection().getKdistNeighbors();
		
		int n = getN();
		int end = minK-1;
		int currentK =getKnnCollection().getK()-1;
		
		// for each k in the range of MinPtsLB to MinPtsUB
		for (; currentK >= end; currentK-- ) {
			// calculate lrd for each point 
			for (int i=0; i< n ; i++ ){
				int cardinality= weight[i]-1;
				double sumReachability = cardinality * neighborDistance[i][currentK];
				
				for (int j=0; j<= currentK ; j++)
				{
					int currentIndex = neighborIndices[i][j];
					int weightNeighbor = weight[currentIndex];
					sumReachability+= weightNeighbor* Math.max(neighborDistance[i][j], neighborDistance[currentIndex][currentK]); 
					cardinality+= weightNeighbor;
				}
				

				for (int currentIndex: kdistNeighbors[i]){
					int weightNeighbor = weight[currentIndex];
					sumReachability+= weightNeighbor* Math.max(neighborDistance[i][currentK], neighborDistance[currentIndex][currentK]); 
					cardinality+= weightNeighbor; 
					
				}
				lrd[i] = cardinality/ sumReachability;
				
			}
			
			// calculate lof for each point
			for (int i=0; i< n ; i++){
				int cardinality= weight[i]-1;
				double sumlrd= cardinality * lrd[i];
				
				for (int j=0; j<= currentK ; j++)
				{
					int currentIndex = neighborIndices[i][j];
					int weightNeighbor = weight[currentIndex];
					sumlrd+= weightNeighbor* lrd[currentIndex]; 
					cardinality+= weightNeighbor;
				}
							
				for (int currentIndex: kdistNeighbors[i]){
					int weightNeighbor = weight[currentIndex];
					sumlrd+= weightNeighbor* lrd[currentIndex]; 
					cardinality+= weightNeighbor; 
					
				}
				double tempLOF = sumlrd/(cardinality* lrd[i]);
			
				// set LOF to the maximum
				if(tempLOF> lof[i])
					lof[i]= tempLOF;
				
			}
			
			// shrink the KNNcollection to size k-1
			getKnnCollection().shrink();
		
			
		}
		return lof;
		
	}
	

}