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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.set.Condition;
import com.rapidminer.example.set.ConditionCreationException;
import com.rapidminer.example.set.ConditionedExampleSet;
import com.rapidminer.gui.RapidMinerGUI;
import com.rapidminer.gui.tools.ExtendedJScrollPane;
import com.rapidminer.gui.tools.ViewToolBar;
import com.rapidminer.gui.viewer.DataViewerTable;
import com.rapidminer.report.Tableable;
import com.rapidminer.tools.ParameterService;

/** 
 * Mostly a copy of The RapidMiner DataViewer class, using OutlierCellColorProiver and
 * OutlierExampleSet.
 *
 */
public class OutlierDataViewer  extends JPanel  implements Tableable  {

    private static final long serialVersionUID = -8114228636932871865L;

    private static final int DEFAULT_MAX_SIZE_FOR_FILTERING = 100000;

    private JLabel generalInfo = new JLabel();
    {
        generalInfo.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
    }

    private DataViewerTable dataTable = new DataViewerTable();

    /** Filter counter display. */
    private JLabel filterCounter = new JLabel();


    private transient ExampleSet originalExampleSet;

    public OutlierDataViewer(OutlierExampleSet exampleSet, boolean providedFilter) {
        super(new BorderLayout());
        this.originalExampleSet = exampleSet;

        ViewToolBar toolBar = new ViewToolBar();

        StringBuffer infoText = new StringBuffer("ExampleSet (");
        int noExamples = originalExampleSet.size();
        infoText.append(noExamples);
        infoText.append(noExamples == 1 ? " example, " : " examples, ");
        int noSpecial = originalExampleSet.getAttributes().specialSize();
        infoText.append(noSpecial);
        infoText.append(noSpecial == 1 ? " special attribute, " : " special attributes, ");
        int noRegular = originalExampleSet.getAttributes().size();
        infoText.append(noRegular);
        infoText.append(noRegular == 1 ? " regular attribute)" : " regular attributes)");
        generalInfo.setText(infoText.toString());
        toolBar.add(generalInfo, ViewToolBar.LEFT);

        // filter
        if (providedFilter) {
            toolBar.add(new JLabel("View Filter "), ViewToolBar.RIGHT);
            updateFilterCounter(originalExampleSet);
            toolBar.add(filterCounter, ViewToolBar.RIGHT);
            List<String> applicableFilterNames = new LinkedList<String>();
            for (String conditionName : ConditionedExampleSet.KNOWN_CONDITION_NAMES) {
                try {
                    ConditionedExampleSet.createCondition(conditionName, exampleSet, null);
                    applicableFilterNames.add(conditionName);
                } catch (ConditionCreationException ex) {} // Do nothing
            }
            String[] applicableConditions = new String[applicableFilterNames.size()];
            applicableFilterNames.toArray(applicableConditions);
            final JComboBox filterSelector = new JComboBox(applicableConditions);
            filterSelector.setToolTipText("These filters can be used to skip examples in the view fulfilling the filter condition.");
            filterSelector.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    updateFilter((String)filterSelector.getSelectedItem());
                }
            });

            int maxNumberBeforeFiltering = DEFAULT_MAX_SIZE_FOR_FILTERING;
            String maxString = ParameterService.getParameterValue(RapidMinerGUI.PROPERTY_RAPIDMINER_GUI_MAX_STATISTICS_ROWS);
            if (maxString != null) {
                try {
                    maxNumberBeforeFiltering = Integer.parseInt(maxString);
                } catch (NumberFormatException e) {
                    // do nothing
                }
            }
            if (exampleSet.size() > maxNumberBeforeFiltering) {
                filterSelector.setEnabled(false);
            }
            toolBar.add(filterSelector, ViewToolBar.RIGHT);
            toolBar.setPreferredSize(new Dimension(getWidth(), 29));
        }

        add(toolBar, BorderLayout.NORTH);
        JScrollPane tableScrollPane = new ExtendedJScrollPane(dataTable);
        tableScrollPane.setBorder(null);
        add(tableScrollPane, BorderLayout.CENTER);
        setExampleSet(exampleSet);
        
        /*
         * set the right cell color Proiver
         */
        final int numberOfSpecialAttributes = exampleSet.getAttributes().specialSize();
        dataTable.setCellColorProvider(new OutlierCellColorProvider(dataTable,exampleSet.colors,numberOfSpecialAttributes, exampleSet));
    }

    public void setExampleSet(ExampleSet exampleSet) {
        dataTable.setExampleSet(exampleSet);
    }

    private void updateFilter(String conditionName) {
        ExampleSet filteredExampleSet = originalExampleSet;
        try {
            Condition condition = ConditionedExampleSet.createCondition(conditionName, originalExampleSet, null);
            filteredExampleSet = new ConditionedExampleSet(originalExampleSet, condition);
        } catch (ConditionCreationException ex) {
            originalExampleSet.getLog().logError("Cannot create condition '" + conditionName + "' for filtered data view: " + ex.getMessage() + ". Using original data set view...");
            filteredExampleSet = originalExampleSet;
        }
        updateFilterCounter(filteredExampleSet);
        setExampleSet(filteredExampleSet);
    }

    private void updateFilterCounter(ExampleSet filteredExampleSet) {
        filterCounter.setText("(" + filteredExampleSet.size() + " / " + originalExampleSet.size() + "): ");
    }

    @Override
    public void prepareReporting() {
        dataTable.prepareReporting();
    }

    @Override
    public void finishReporting() {
        dataTable.finishReporting();
    }

    @Override
    public String getColumnName(int columnIndex) {
        return dataTable.getColumnName(columnIndex);
    }

    @Override
    public String getCell(int row, int column) {
        return dataTable.getCell(row, column);
    }
   
    @Override
    public int getColumnNumber() {
        return dataTable.getColumnNumber();
    }

    @Override
    public int getRowNumber() {
        return dataTable.getRowNumber();
    }

    @Override
    public boolean isFirstLineHeader() { return false; }

    @Override
    public boolean isFirstColumnHeader() { return false; }
   
  
   

}
