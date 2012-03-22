/*
 *  RapidMiner Anomaly Detection Extension
 *
 *  Copyright (C) 2009-2012 by Deutsches Forschungszentrum fuer
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
 * Author: Ahmed Elsawy (ahmed.nagah.elsawy@gmail.com)
 * Responsible: Markus Goldstein (Markus.Goldstein@dfki.de)
 *
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */
package de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based;

import java.util.HashMap;
import java.util.Collection;
import com.rapidminer.tools.RandomGenerator;
import com.rapidminer.tools.math.similarity.DistanceMeasure;

import de.dfki.madm.anomalydetection.evaluator.Evaluator;

/**
 * This is the implementation of aLOCI (Fast Outlier Detection Using the Local
 * Coorelation Integeral) proposed by S. papdimitriou el al (2003)
 * 
 * @author Ahmed Elsawy
 * 
 */

public class ALOCIEvaluator implements Evaluator {
	private DistanceMeasure measure;
	private TreeNode[] root;
	private int dimensions, levels, alpha;
	private double Rp;
	private double[][] points;
	private double[][] grids;
	private double[] scores;
	private int nmin;
	private RandomGenerator rg;
	private boolean parallelProcess;
	private int numberOfThreads;

	/*
	 * Parallelizing creation of Quad-trees
	 */
	private class ALOCIThread extends Thread {
		int start, end;

		public ALOCIThread(int start, int end) {
			this.start = start;
			this.end = end;
		}

		public void run() {
			for (int g = start; g <= end; ++g) {
				root[g] = new TreeNode(points.length);

				for (int i = 0; i < points.length; ++i) {
					double r = Rp / 2;
					double[] center = createPoint(dimensions, r);

					TreeNode currentTreeNode = root[g];
					for (int j = 0; j < levels; ++j) {
						int index = cellFinder(points[i], center, grids[g],
								r / 2);
						currentTreeNode = currentTreeNode.createChild(index);
						r /= 2;
					}
				}
			}
		}
	}

	public ALOCIEvaluator(DistanceMeasure measure, double[][] points,
			int numOfGrids, int levels, int alpha, int nmin,
			RandomGenerator rg, boolean parallelProcess, int numberOfThreads) {
		this.measure = measure;
		this.points = changePoints(points);
		this.levels = levels;
		this.alpha = alpha;
		this.dimensions = points[0].length;
		this.nmin = nmin;
		this.grids = new double[numOfGrids][dimensions];
		this.scores = new double[points.length];
		this.rg = rg;
		this.parallelProcess = parallelProcess;
		this.numberOfThreads = Math.min(numberOfThreads, grids.length);

		for (int i = 0; i < scores.length; ++i)
			scores[i] = Double.MIN_VALUE;
	}

	/*
	 * calculate max score for each point
	 */
	private void calculateAllScores() {
		TreeNode[] counting = new TreeNode[grids.length];
		TreeNode[] sampling = new TreeNode[grids.length];

		double[][] countingCenter = new double[grids.length][dimensions];
		double[][] samplingCenter = new double[grids.length][dimensions];

		for (int p = 0; p < points.length; ++p) {
			for (int g = 0; g < grids.length; ++g) {
				counting[g] = root[g];
				sampling[g] = root[g];

				countingCenter[g] = createPoint(dimensions, Rp / 2);
				samplingCenter[g] = createPoint(dimensions, Rp / 2);
			}

			double countingRadius = Rp;
			double samplingRadius = Rp;

			for (int level = 0; level < alpha; ++level) {
				countingRadius /= 2;
				for (int g = 0; g < grids.length; ++g) {
					int index = cellFinder(points[p], countingCenter[g],
							grids[g], countingRadius / 2);
					counting[g] = counting[g].getChild(index);
				}
			}
			for (int level = alpha; level <= levels; ++level) {
				double dist = Double.MAX_VALUE;
				int cellIndex = -1;
				for (int g = 0; g < grids.length; ++g) {
					double newDistance = measure.calculateDistance(
							move(points[p], grids[g], true), countingCenter[g]);
					if (newDistance < dist) {
						dist = newDistance;
						cellIndex = g;
					}
				}
				dist = Double.MAX_VALUE;
				int cellIndex2 = -1;
				for (int g = 0; g < grids.length; ++g) {
					double newDistance = measure.calculateDistance(
							move(samplingCenter[g], grids[g], false),
							move(countingCenter[cellIndex], grids[cellIndex],
									false));
					if (newDistance < dist) {
						dist = newDistance;
						cellIndex2 = g;
					}
				}
				countingRadius /= 2;
				samplingRadius /= 2;
				calculateScore(counting[cellIndex], sampling[cellIndex2], p,
						level, samplingCenter[cellIndex2]);

				if (level < levels)
					for (int g = 0; g < grids.length; ++g) {
						int nextChild = cellFinder(points[p],
								countingCenter[g], grids[g], countingRadius / 2);
						counting[g] = counting[g].getChild(nextChild);
						nextChild = cellFinder(points[p], samplingCenter[g],
								grids[g], samplingRadius / 2);
						sampling[g] = sampling[g].getChild(nextChild);
					}
			}
		}
	}

	private double[] move(double[] p1, double[] shift1, boolean sign) {
		double[] result = new double[p1.length];
		if (sign)
			for (int i = 0; i < p1.length; ++i)
				result[i] = p1[i] - shift1[i];
		else
			for (int i = 0; i < p1.length; ++i)
				result[i] = p1[i] + shift1[i];

		return result;
	}

	private void calculateScore(TreeNode ci, TreeNode cj, int p, int level,
			double[] center) {
		if (cj.cj >= nmin) {
			double n = ci.cj;
			double nAverage = sq(cj, 2, n) / sq(cj, 1, n);
			double deviation = Math.sqrt(((sq(cj, 3, n)) / sq(cj, 1, n))
					- (Math.pow((sq(cj, 2, n) / sq(cj, 1, n)), 2)));

			if (deviation != 0)
				scores[p] = Math.max((nAverage - n) / deviation, scores[p]);
		}
	}

	/*
	 * create Quad-trees with no parallelization
	 */
	private void createQuadTree() {
		root = new TreeNode[grids.length];
		for (int g = 0; g < grids.length; ++g) {
			root[g] = new TreeNode(points.length);
			for (int i = 0; i < points.length; ++i) {
				double r = Rp / 2;
				double[] center = createPoint(dimensions, r);

				TreeNode currentTreeNode = root[g];
				for (int j = 0; j < levels; ++j) {
					int index = cellFinder(points[i], center, grids[g], r / 2);
					currentTreeNode = currentTreeNode.createChild(index);
					r /= 2;
				}
			}
		}
	}

	/*
	 * create Quad-trees with parallelization
	 */
	private void createQuadTreeParallel() {
		root = new TreeNode[grids.length];

		int bulk = (int) Math.ceil((double) grids.length / numberOfThreads);

		ALOCIThread[] threads = new ALOCIThread[numberOfThreads];

		for (int i = 0; i < numberOfThreads; ++i) {
			threads[i] = new ALOCIThread(i * bulk, Math.min(
					i * bulk + bulk - 1, grids.length - 1));
			threads[i].start();
		}
		for (int i = 0; i < numberOfThreads; ++i)
			try {
				threads[i].join();
			} catch (InterruptedException e) {

				e.printStackTrace();
			}
	}

	private double[] createPoint(int d, double value) {
		double[] point = new double[d];
		for (int i = 0; i < d; ++i)
			point[i] = value;
		return point;
	}

	/*
	 * Finds the index of the cell where point p should be inserted
	 */
	private int cellFinder(double[] p, double[] center, double[] shift,
			double move) {
		int result = 0;
		for (int i = 0; i < p.length; ++i)
			if (p[i] - shift[i] > center[i]) {
				center[i] += move;
				result = result | (1 << i);
			} else
				center[i] -= move;
		return result;
	}

	/*
	 * Changes the location of all the points such that the minimum value in all
	 * dimension is 0
	 */
	private double[][] changePoints(double[][] points) {
		double[] min = new double[points[0].length];
		for (int i = 0; i < min.length; ++i) {
			min[i] = Double.MAX_VALUE;
			for (int j = 0; j < points.length; ++j) {
				if (min[i] > points[j][i])
					min[i] = points[j][i];
			}
		}
		double[][] result = new double[points.length][points[0].length];
		for (int i = 0; i < min.length; ++i)
			for (int j = 0; j < points.length; ++j)
				result[j][i] = points[j][i] - min[i];

		return result;
	}

	private double sq(TreeNode n, int q, double counting) {
		double result = sqRecursive(n, q, 0, alpha);
		return result;
	}

	/*
	 * Traverses the quadtree to reach the alphaR values in the sampling
	 * neighborhood
	 */
	private double sqRecursive(TreeNode n, int q, int l, int max) {
		if (l == max)
			return Math.pow(n.cj, q);
		Collection<TreeNode> c = n.children.values();
		Object[] children = c.toArray();
		double result = 0;
		for (int i = 0; i < children.length; ++i)
			result += sqRecursive((TreeNode) children[i], q, l + 1, max);
		return result;
	}

	/*
	 * This method creates shifts for the quadtree the size of the root of the
	 * quadtree is Rp which is twice the size of the largest value in all
	 * dimensions
	 */
	private void createShifts() {
		double[] maxValues = new double[dimensions];
		for (int i = 0; i < points.length; ++i)
			for (int j = 0; j < points[0].length; ++j) {
				Rp = Math.max(Rp, points[i][j]);
				if (points[i][j] > maxValues[j])
					maxValues[j] = points[i][j];
			}

		Rp *= 2;

		for (int i = 0; i < grids.length; ++i)
			for (int j = 0; j < dimensions; ++j)
				grids[i][j] = rg.nextDoubleInRange(-(Rp - maxValues[j]), 0);
	}

	@Override
	public double[] evaluate() {
		createShifts();
		if (!parallelProcess)
			createQuadTree();
		else
			createQuadTreeParallel();

		calculateAllScores();
		return scores;
	}
}

class TreeNode {
	public int cj;
	public HashMap<Integer, TreeNode> children;

	public TreeNode(int cj) {
		this.cj = cj;
	}

	public TreeNode() {
	}

	public TreeNode createChild(int index) {
		if (children == null)
			children = new HashMap<Integer, TreeNode>(10);

		TreeNode currentTreeNode = children.get(index);

		if (currentTreeNode == null) {
			currentTreeNode = new TreeNode();
			children.put(index, currentTreeNode);
		}
		currentTreeNode.cj++;
		return currentTreeNode;
	}

	public TreeNode getChild(int index) {
		return children.get(index);
	}
}