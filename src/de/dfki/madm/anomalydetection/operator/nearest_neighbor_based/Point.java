package de.dfki.madm.anomalydetection.operator.nearest_neighbor_based;



public class Point implements Comparable<Point> {
	public int getIndex() {
		return index;
	}

	double[] point;
	int index;

	public Point(int index, double[] point) {
		this.index = index;
		this.point = point;
	}

	@Override
	public int compareTo(Point arg0) {
		int n = point.length;
		for (int i = 0; i < n; i++)
			if (point[i] != arg0.point[i])
				if (point[i] < arg0.point[i])
					return -1;
				else
					return 1;

		return 0;
	}

	public boolean equals(Object obj) {

		return compareTo((Point) obj) == 0;
	}

}
