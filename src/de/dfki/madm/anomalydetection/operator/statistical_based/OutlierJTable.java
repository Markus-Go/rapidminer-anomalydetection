/*
 *  RapidMiner Anomaly Detection Extension
 *
 *  Copyright (C) 2009-2014 by Deutsches Forschungszentrum fuer
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

package de.dfki.madm.anomalydetection.operator.statistical_based;

import java.awt.Color;
import javax.swing.table.TableCellRenderer;

import com.rapidminer.gui.tools.CellColorProvider;
import com.rapidminer.gui.tools.ColoredTableCellRenderer;
import com.rapidminer.gui.tools.ExtendedJTable;

public class OutlierJTable extends ExtendedJTable {
	private transient ColoredTableCellRenderer renderer = new ColoredTableCellRenderer();
	public OutlierJTable() {
		super();
	}
	 @Override
	    public TableCellRenderer getCellRenderer(int row, int col) {
		 		System.out.println("test");
	            renderer.setColor(Color.red);
	            renderer.setDateFormat(getDateFormat(row, convertColumnIndexToModel(col)));
	            return renderer;
	       
	    }
}
