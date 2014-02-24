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
import java.util.HashMap;
import com.rapidminer.gui.tools.CellColorProvider;
import com.rapidminer.gui.tools.SwingTools;
import com.rapidminer.gui.viewer.DataViewerTable;
/**
 * Provides the colors of the cells in the output table.
 * 
 */
public class OutlierCellColorProvider implements CellColorProvider{
	
	private HashMap<Integer,int[]> colors;
	private int numberOfSpecialAttributes;
	private int color;
	private OutlierExampleSet exampleSet;
	private DataViewerTable dataTable;
	
	public OutlierCellColorProvider(DataViewerTable dataTable,HashMap<Integer,int[]> colors,int numberOfSpecialAttributes,OutlierExampleSet exampleSet) {
		this.colors = colors;
		this.numberOfSpecialAttributes = numberOfSpecialAttributes;
		this.dataTable = dataTable;
		this.exampleSet = exampleSet;
		}
	
	@Override
	public Color getCellColor(int row, int column) {
		int col = dataTable.convertColumnIndexToModel(column);
		if(col <= numberOfSpecialAttributes) {
			if(col == 0) {
				// first column (index)
				if (row % 2 == 0) {
		            return Color.WHITE;
			} else {
		        	return SwingTools.LIGHTEST_BLUE;
			     }
			}
		   if (row % 2 == 0) {
			    //i special attributes (like score)
	            return Color.WHITE;
		   } else {
	        	return SwingTools.LIGHTEST_YELLOW;
	       }
		}
		else {
			//i row index
			String index = dataTable.getCell(row+1,0);
			// attribute name
			String attribute_name = dataTable.getCell(0,column);
			// index of that attribute needed to fetch the right color value
			int tableIndex = exampleSet.getAttributes().get(attribute_name).getTableIndex();
			color = (int)colors.get(tableIndex)[Integer.parseInt(index)-1];

			/*transform the integer values into actual colors.
			 * 510 => green (0,255,0)
			 * 255 => yellow (255,255,0)
			 * 0 => red (255,0,0)
			 * ...
			 */
			if(color == -1){
				return Color.white;
			}
			else if(color >= 255) {
				return new Color((510-color), 255, 0);
			}
			// second half of colors go from yellow to red (unnormal bins)
			else{
				return new Color(255,(color) , 0);
			}
		}
		
    }	

}
