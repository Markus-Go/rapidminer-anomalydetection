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
