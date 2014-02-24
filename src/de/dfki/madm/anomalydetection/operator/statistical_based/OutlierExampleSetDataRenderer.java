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

import java.awt.Component;
import com.rapidminer.datatable.DataTable;
import com.rapidminer.datatable.DataTableExampleSetAdapter;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.gui.renderer.AbstractDataTableTableRenderer;

import com.rapidminer.gui.tools.CellColorProvider;
import com.rapidminer.gui.tools.CellColorProviderAlternating;
import com.rapidminer.gui.tools.ExtendedJTable;
import com.rapidminer.operator.IOContainer;
/** new Renderer for the color coded output for HBOS.
 * 
 */
public class OutlierExampleSetDataRenderer extends AbstractDataTableTableRenderer  {
	 public static final String RENDERER_NAME = "Outlier Data View";

	 @Override
	 public String getName() {
		return RENDERER_NAME;
	 }
	@Override
	public Component getVisualizationComponent(Object renderable, IOContainer ioContainer) {
		/*DataTable dataTable = getDataTable(renderable, ioContainer, true);
		if (dataTable != null) {
			DataTableViewerTable resultTable = new DataTableViewerTable(dataTable, isSortable(), isColumnMovable(), isAutoresize());
			resultTable.setCellColorProvider(getCellColorProvider(resultTable, renderable));
			return new ExtendedJScrollPane(resultTable);
		} else {
			return new JLabel("No visualization possible for table.");
		}*/
		OutlierExampleSet exampleSet = (OutlierExampleSet)renderable;
        return new OutlierDataViewer(exampleSet,true);
	}

	@Override
	protected CellColorProvider getCellColorProvider(ExtendedJTable table, Object renderable) {
		return new CellColorProviderAlternating();
	}
	@Override
	public DataTable getDataTable(Object renderable, IOContainer container, boolean isRendering) {
		 ExampleSet exampleSet = (ExampleSet)renderable;
		return new DataTableExampleSetAdapter(exampleSet,null);
	}

		
	}
