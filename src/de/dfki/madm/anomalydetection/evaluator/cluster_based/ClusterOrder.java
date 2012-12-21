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
 * Author: Patrick Kalka
 * Responsible: Markus Goldstein (Markus.Goldstein@dfki.de)
 * 
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */

package de.dfki.madm.anomalydetection.evaluator.cluster_based;

import java.util.Arrays;

/**
 * 
 * 
 * @author Mennatallah Amer
 *
 */

public class ClusterOrder implements Comparable<ClusterOrder> {
	private int clusterIndex;
	private int clusterSize;
	public ClusterOrder(int index, int size) {
		clusterIndex= index;
		clusterSize= size;
	}
	@Override
	public int compareTo(ClusterOrder o) {
		
		return o.clusterSize- clusterSize;
	}
	public int getClusterIndex() {
		return clusterIndex;
	}
	public int getClusterSize() {
		return clusterSize;
	}
	
	public static ClusterOrder[] getOrderedClusters(int [] clusterSize){
		int numberOfClusters= clusterSize.length;
		ClusterOrder[]clusterOrders= new ClusterOrder[numberOfClusters];
		for (int i=0; i< numberOfClusters; i++){
			clusterOrders[i]= new ClusterOrder(i, clusterSize[i]);
		}
		Arrays.sort(clusterOrders);
		return clusterOrders;
	}
	
	
}
