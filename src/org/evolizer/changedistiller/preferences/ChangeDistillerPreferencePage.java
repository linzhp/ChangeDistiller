/*
 * Copyright 2009 University of Zurich, Switzerland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.evolizer.changedistiller.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.evolizer.changedistiller.ChangeDistillerPlugin;

/**
 * This class represents a preference page that is contributed to the Preferences dialog. By subclassing
 * <samp>FieldEditorPreferencePage</samp>, we can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the preference store that belongs to the main
 * plug-in class. That way, preferences can be accessed directly via the preference store.
 * 
 * @author fluri
 */
public class ChangeDistillerPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private static final int TEXT_LIMIT = 4;
    private ComboFieldEditor fLeafStringSims;
    private IntegerFieldEditor fLeafNGramsValue;
    private BooleanFieldEditor fNodeStringSimEnablement;
    private Group fNodeStringSimilarity;
    private ComboFieldEditor fNodeStringSim;
    private IntegerFieldEditor fNodeNGramsValue;
    private BooleanFieldEditor fDynamicThresholdEnablement;

    private Group fDynamicThreshold;
    private IPreferenceStore fStore;

    private Composite fNodeStringSimSpacer;

    /**
     * Creates a new ChangeDistiller preference page
     */
    public ChangeDistillerPreferencePage() {
        super(GRID);
        super.setDescription(Messages.sPreferencePageDescription);
        setPreferenceStore(ChangeDistillerPlugin.getDefault().getPreferenceStore());
    }

    /**
     * {@inheritDoc}
     */
    public void init(IWorkbench workbench) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (event.getSource() == fLeafStringSims) {
            boolean enabled = event.getNewValue().equals(IChangeDistillerPreferenceConstants.PREF_STRING_SIM_NGRAMS);
            fLeafNGramsValue.setEnabled(enabled, getFieldEditorParent());
        } else if (event.getSource() == fNodeStringSimEnablement) {
            boolean enabled = fNodeStringSimEnablement.getBooleanValue();
            fNodeStringSimilarity.setEnabled(enabled);
        } else if (event.getSource() == fNodeStringSim) {
            boolean enabled = event.getNewValue().equals(IChangeDistillerPreferenceConstants.PREF_STRING_SIM_NGRAMS);
            fNodeNGramsValue.setEnabled(enabled, fNodeStringSimSpacer);
        } else if (event.getSource() == fDynamicThresholdEnablement) {
            boolean enabled = fDynamicThresholdEnablement.getBooleanValue();
            fDynamicThreshold.setEnabled(enabled);
        }
        super.propertyChange(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void createFieldEditors() {
        fLeafStringSims =
                new ComboFieldEditor(
                        IChangeDistillerPreferenceConstants.LEAF_STRING_SIM,
                        Messages.sLeafStringSim,
                        new String[][]{
                                {
                                        Messages.sStringSimLevenshtein,
                                        IChangeDistillerPreferenceConstants.PREF_STRING_SIM_LEVENSHTEIN},
                                {Messages.sStringSimNGrams, IChangeDistillerPreferenceConstants.PREF_STRING_SIM_NGRAMS}},
                        getFieldEditorParent());
        addField(fLeafStringSims);

        fLeafNGramsValue =
                new IntegerFieldEditor(
                        IChangeDistillerPreferenceConstants.LEAF_NGRAMS_VALUE,
                        Messages.sNGramsValue,
                        getFieldEditorParent(),
                        2);
        getPreferenceStore();
        addField(fLeafNGramsValue);

        addField(new DoubleFieldEditor(
                IChangeDistillerPreferenceConstants.LEAF_STRING_SIM_THRESHOLD,
                Messages.sLeafStringSimThreshold,
                getFieldEditorParent(),
                TEXT_LIMIT));

        fNodeStringSimEnablement =
                new BooleanFieldEditor(
                        IChangeDistillerPreferenceConstants.NODE_STRING_SIM_ENABLEMENT,
                        Messages.sNodeStringSimEnablement,
                        getFieldEditorParent());
        addField(fNodeStringSimEnablement);

        fNodeStringSimilarity = createGroup(getFieldEditorParent(), "", 1, 2, GridData.FILL_HORIZONTAL);
        fNodeStringSimSpacer = createComposite(fNodeStringSimilarity, 1, 1, GridData.FILL_HORIZONTAL);
        fNodeStringSim =
                new ComboFieldEditor(
                        IChangeDistillerPreferenceConstants.NODE_STRING_SIM,
                        Messages.sNodeStringSim,
                        new String[][]{
                                {
                                        Messages.sStringSimLevenshtein,
                                        IChangeDistillerPreferenceConstants.PREF_STRING_SIM_LEVENSHTEIN},
                                {Messages.sStringSimNGrams, IChangeDistillerPreferenceConstants.PREF_STRING_SIM_NGRAMS}},
                        fNodeStringSimSpacer);
        fNodeStringSim.fillIntoGrid(fNodeStringSimSpacer, 2);
        addField(fNodeStringSim);

        fNodeNGramsValue =
                new IntegerFieldEditor(
                        IChangeDistillerPreferenceConstants.NODE_STRING_SIM_NGRAMS_VALUE,
                        Messages.sNGramsValue,
                        fNodeStringSimSpacer,
                        2);
        fNodeNGramsValue.fillIntoGrid(fNodeStringSimSpacer, 2);
        addField(fLeafNGramsValue);

        FieldEditor editor =
                new DoubleFieldEditor(
                        IChangeDistillerPreferenceConstants.NODE_STRING_SIM_THRESHOLD,
                        Messages.sNodeStringSimThreshold,
                        fNodeStringSimSpacer,
                        TEXT_LIMIT);
        editor.fillIntoGrid(fNodeStringSimSpacer, 2);
        addField(editor);

        addField(new ComboFieldEditor(
                IChangeDistillerPreferenceConstants.NODE_SIM,
                Messages.sNodeSim,
                new String[][]{
                        {Messages.sNodeSimChawathe, IChangeDistillerPreferenceConstants.PREF_NODE_SIM_CHAWATHE},
                        {Messages.sNodeSimDice, IChangeDistillerPreferenceConstants.PREF_NODE_SIM_DICE}},
                getFieldEditorParent()));
        addField(new DoubleFieldEditor(
                IChangeDistillerPreferenceConstants.NODE_SIM_THRESHOLD,
                Messages.sNodeSimThreshold,
                getFieldEditorParent(),
                TEXT_LIMIT));

        fDynamicThresholdEnablement =
                new BooleanFieldEditor(
                        IChangeDistillerPreferenceConstants.DYNAMIC_THRESHOLD_ENABLEMENT,
                        Messages.sDynamicThresholdEnablement,
                        getFieldEditorParent());
        addField(fDynamicThresholdEnablement);

        fDynamicThreshold = createGroup(getFieldEditorParent(), "", 1, 2, GridData.FILL_HORIZONTAL);
        Composite spacer = createComposite(fDynamicThreshold, 1, 1, GridData.FILL_HORIZONTAL);
        editor =
                new IntegerFieldEditor(IChangeDistillerPreferenceConstants.DEPTH_VALUE, Messages.sDepthValue, spacer, 2);
        editor.fillIntoGrid(spacer, 2);
        addField(editor);

        editor =
                new DoubleFieldEditor(
                        IChangeDistillerPreferenceConstants.DYNAMIC_THRESHOLD,
                        Messages.sDynamicThreshold,
                        spacer,
                        TEXT_LIMIT);
        editor.fillIntoGrid(spacer, 2);
        addField(editor);

        addField(new ComboFieldEditor(
                IChangeDistillerPreferenceConstants.LEAF_MATCHING,
                Messages.sLeafMatching,
                new String[][]{
                        {Messages.sLeafMatchingFirst, IChangeDistillerPreferenceConstants.PREF_LEAF_MATCHING_FIRST},
                        {Messages.sLeafMatchingBest, IChangeDistillerPreferenceConstants.PREF_LEAF_MATCHING_BEST}},
                getFieldEditorParent()));
        fStore = ChangeDistillerPlugin.getDefault().getPreferenceStore();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        super.initialize();
        fLeafNGramsValue.setEnabled(isLeafNGramsEditEnabled(), getFieldEditorParent());
        fNodeStringSimilarity.setEnabled(isNodeStringSimEnabled());
        fNodeNGramsValue.setEnabled(isNodeNGramsEditEnabled(), fNodeStringSimSpacer);
        fDynamicThreshold.setEnabled(isDynamicThresholdEnabled());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void performDefaults() {
        super.performDefaults();
        fLeafNGramsValue.setEnabled(true, getFieldEditorParent());
    }

    private static Composite createComposite(Composite parent, int columns, int hspan, int fill) {
        Composite g = new Composite(parent, SWT.NONE);
        g.setLayout(new GridLayout(columns, false));
        g.setFont(parent.getFont());
        GridData gd = new GridData(fill);
        gd.horizontalSpan = hspan;
        g.setLayoutData(gd);
        return g;
    }

    private Group createGroup(Composite parent, String text, int columns, int hspan, int fill) {
        Group g = new Group(parent, SWT.NONE);
        g.setLayout(new GridLayout(columns, false));
        g.setText(text);
        g.setFont(parent.getFont());
        GridData gd = new GridData(fill);
        gd.horizontalSpan = hspan;
        g.setLayoutData(gd);
        return g;
    }

    private boolean isDynamicThresholdEnabled() {
        return fStore.getBoolean(IChangeDistillerPreferenceConstants.DYNAMIC_THRESHOLD_ENABLEMENT);
    }

    private boolean isLeafNGramsEditEnabled() {
        return fStore.getString(IChangeDistillerPreferenceConstants.LEAF_STRING_SIM).equals(
                IChangeDistillerPreferenceConstants.PREF_STRING_SIM_NGRAMS);
    }

    private boolean isNodeNGramsEditEnabled() {
        return fStore.getString(IChangeDistillerPreferenceConstants.NODE_STRING_SIM).equals(
                IChangeDistillerPreferenceConstants.PREF_STRING_SIM_NGRAMS);
    }

    private boolean isNodeStringSimEnabled() {
        return fStore.getBoolean(IChangeDistillerPreferenceConstants.NODE_STRING_SIM_ENABLEMENT);
    }
}
