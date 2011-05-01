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

import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

/**
 * A field editor for a double type preference. Provides access to this editors refreshValidState() method.
 * 
 * @author jakob
 */
public class DoubleFieldEditor extends StringFieldEditor {

    private static final int DEFAULT_TEXT_LIMIT = 3;
    private double fMinValidValue;
    private double fMaxValidValue = Double.MAX_VALUE;

    /**
     * Creates a new double field editor
     */
    protected DoubleFieldEditor() {}

    /**
     * Creates a double field editor.
     * 
     * @param name
     *            the name of the preference this field editor works on
     * @param labelText
     *            the label text of the field editor
     * @param parent
     *            the parent of the field editor's control
     */
    public DoubleFieldEditor(String name, String labelText, Composite parent) {
        this(name, labelText, parent, DEFAULT_TEXT_LIMIT);
    }

    /**
     * Creates a double field editor.
     * 
     * @param name
     *            the name of the preference this field editor works on
     * @param labelText
     *            the label text of the field editor
     * @param parent
     *            the parent of the field editor's control
     * @param textLimit
     *            the maximum number of characters in the text.
     */
    public DoubleFieldEditor(String name, String labelText, Composite parent, int textLimit) {
        init(name, labelText);
        setTextLimit(textLimit);
        setEmptyStringAllowed(false);
        setErrorMessage(JFaceResources.getString("IntegerFieldEditor.errorMessage")); //$NON-NLS-1$
        createControl(parent);
    }

    /**
     * Sets the range of valid values for this field.
     * 
     * @param min
     *            the minimum allowed value (inclusive)
     * @param max
     *            the maximum allowed value (inclusive)
     */
    public void setValidRange(double min, double max) {
        fMinValidValue = min;
        fMaxValidValue = max;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean checkState() {

        Text text = getTextControl();

        if (text == null) {
            return false;
        }

        String numberString = text.getText();
        try {
            double number = Double.valueOf(numberString).doubleValue();
            if ((number >= fMinValidValue) && (number <= fMaxValidValue)) {
                clearErrorMessage();
                return true;
            }

            showErrorMessage();
            return false;

        } catch (NumberFormatException e) {
            showErrorMessage();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doLoad() {
        Text text = getTextControl();
        if (text != null) {
            double value = getPreferenceStore().getDouble(getPreferenceName());
            text.setText("" + value); //$NON-NLS-1$
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doLoadDefault() {
        Text text = getTextControl();
        if (text != null) {
            double value = getPreferenceStore().getDefaultDouble(getPreferenceName());
            text.setText("" + value); //$NON-NLS-1$
        }
        valueChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doStore() {
        Text text = getTextControl();
        if (text != null) {
            Double i = new Double(text.getText());
            getPreferenceStore().setValue(getPreferenceName(), i.doubleValue());
        }
    }

    /**
     * Returns this field editor's current value as a double.
     * 
     * @return the value
     * @exception NumberFormatException
     *                if the <code>String</code> does not contain a parsable double
     */
    public double getDoubleValue() throws NumberFormatException {
        return new Double(getStringValue()).doubleValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refreshValidState() {
        super.refreshValidState();
    }

    /**
     * Sets the tool tip text of the label and text control.
     * 
     * @param text
     *            the text to set
     */
    public void setToolTipText(String text) {
        this.getLabelControl().setToolTipText(text);
        this.getTextControl().setToolTipText(text);
    }
}
