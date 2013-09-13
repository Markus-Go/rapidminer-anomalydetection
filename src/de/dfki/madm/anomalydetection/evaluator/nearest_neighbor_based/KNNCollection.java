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

import java.io.Serializable;
import java.util.LinkedList;

/**
 * 
 * This class contains all the information for the nearest neighborhood set of
 * size k.
 * 
 * 
 * @author Mennatallah Amer
 * 
 */
public class KNNCollection implements Serializable {
	/**
	 *  Change this if the object changes
	 */
	private static final long serialVersionUID = 123456L;

	/** The size of the data **/
	int n;

	/** The number of nearest neighbors **/
	int k;

	/** The indicies of the neighbors in the k nearest neighbors set **/
	private int[][] neighborIndicies;

	/**
	 * The distances of the neighbors in the k nearest neighbors set put in
	 * ascending order.
	 **/
	private double[][] neighborDistances;


	/**
	 * number of neighbors with distinct spatial coordinates in the nearest
	 * neighbor distances.
	 **/
	private int[] numberOfNeighbors;

	/** The points in the exampleSet **/
	private double[][] points;

	/**
	 * Contains the elements that have the distance equal to the
	 * kth-nearest-neighbor-distance because they should be included in the set
	 **/
	private LinkedList<Integer>[] kdistNeighbors;

	/**
	 * The weight of the points. Which corresponds to the number of elements in
	 * the example set that have these coordinates.
	 **/
	private int[] weight;

	@SuppressWarnings("unchecked")
	public KNNCollection(int n, int k, double[][] points, int[] weight) {
		this.n = n;
		this.k = k;
		this.points = points;
		this.weight = weight;

		neighborIndicies = new int[n][k];
		neighborDistances = new double[n][k];
		numberOfNeighbors = new int[n];
		kdistNeighbors = new LinkedList[n];

		for (int i = 0; i < n; i++)
			kdistNeighbors[i] = new LinkedList<Integer>();

	}

	public int getK() {
		return k;
	}

	public LinkedList<Integer>[] getKdistNeighbors() {
		return kdistNeighbors;
	}

	public int getN() {
		return n;
	}

	public double[][] getNeighBorDistanceSoFar() {
		return neighborDistances;
	}

	public int[][] getNeighBorIndiciesSoFar() {
		return neighborIndicies;
	}

	public int[] getNumberOfNeighborsSoFar() {
		return numberOfNeighbors;
	}

	public double[][] getPoints() {
		return points;
	}

	public int[] getWeight() {
		return weight;
	}

	public void shrink(int shrinkBy){
		for (int i=0; i< shrinkBy; i++)
			shrink();
	}
	/**
	 * This method shrinks the kNNCollection to k-1
	 */
	public void shrink() {
		k--;
		if (k == 0)
			return;
		for (int index = 0; index < n; index++) {
			// reduce the number of distinct neighbors by 1
			numberOfNeighbors[index]--;
			// removed index is equal to the old numberofNeighbors -1 which is equal to the new number of neighbors
			int removedIndex = numberOfNeighbors[index];
			int newLast = removedIndex - 1;
			if (neighborDistances[index][newLast] == neighborDistances[index][removedIndex]) {
				kdistNeighbors[index]
						.add(neighborIndicies[index][removedIndex]);
			} else
				kdistNeighbors[index].clear();
		}

	}

	/**
	 * This method updates the KNNcollection by adding the currentDistance and
	 * point2 to the set of the nearest neighbors of point1 if applicable.
	 * 
	 * @param point1
	 *            The point we are updating the neighborhood set for.
	 * @param point2
	 * @param currentDistance
	 *            The distance between point1 and point2.
	 */
	public void updateNearestNeighbors(int point1, int point2,
			double currentDistance) {
		// if this is the first neighbor then add it to the neighborhood set.
		if (numberOfNeighbors[point1] == 0) {
			neighborIndicies[point1][0] = point2;
			neighborDistances[point1][0] = currentDistance;
			numberOfNeighbors[point1]++;
			return;
		}

		int last = numberOfNeighbors[point1] - 1;

		// if the number of neighbors is less than k or the currentDistance is
		// less than the max distance in the neighborhood so far then add point2
		// to the set

		if (neighborDistances[point1][last] >= currentDistance
				|| numberOfNeighbors[point1] < k) {

			boolean flag = true;
			if (numberOfNeighbors[point1] < k)
				numberOfNeighbors[point1]++;
			else {

				if (neighborDistances[point1][last] == currentDistance) {
					// if the current distance as the maximum distance then the
					// point should be added to the nearest neighborhood set
					kdistNeighbors[point1].add(point2);
					flag = false;
				} else {

					if (last > 0
							&& neighborDistances[point1][last - 1] == neighborDistances[point1][last])
						// if the maximum distance is the same as the second
						// maximum distace then last point which is going to
						// removed should be added to the list.
						kdistNeighbors[point1]
								.add(neighborIndicies[point1][last]);
					else
						// else the kdist neighbors are reset.
						// kdistNeighbors[point1].empty();
						kdistNeighbors[point1].clear();
				}
			}

			// Adding point2 to the neighborhood in the appropriate position
			// using insertion sort.

			if (flag) {
				int i = Math.min(last, k - 2);
				for (; i >= 0; i--)
					if (neighborDistances[point1][i] > currentDistance) {
						neighborDistances[point1][i + 1] = neighborDistances[point1][i];
						neighborIndicies[point1][i + 1] = neighborIndicies[point1][i];
					} else
						break;

				neighborDistances[point1][i + 1] = currentDistance;
				neighborIndicies[point1][i + 1] = point2;
			}
		}
	}
	public static KNNCollection clone(KNNCollection a){
		KNNCollection ret = new KNNCollection(a.n,a.k,a.points,a.weight);
		ret.neighborIndicies = a.neighborIndicies.clone();
		ret.neighborDistances = a.neighborDistances.clone(); 
		ret.numberOfNeighbors = a.numberOfNeighbors.clone();
		ret.kdistNeighbors = a.kdistNeighbors.clone();
			return ret;
	}
}
