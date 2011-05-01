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

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.evolizer.changedistiller.ChangeDistillerPlugin;

/**
 * Initializer for ChangeDistiller preference.
 * 
 * @author fluri
 * 
 */
public class ChangeDistillerPreferenceInitializer extends AbstractPreferenceInitializer {

    private final double fLeafStringSimilarityThreshold = 0.6;
    private final int fDynamicThresholdDepthValue = 4;
    private final double fDynamicThreshold = 0.4;
    private final double fNodeSimilarityThreshold = 0.6;

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = ChangeDistillerPlugin.getDefault().getPreferenceStore();

        store.setDefault(
                IChangeDistillerPreferenceConstants.LEAF_STRING_SIM,
                IChangeDistillerPreferenceConstants.PREF_STRING_SIM_NGRAMS);
        store.setDefault(IChangeDistillerPreferenceConstants.LEAF_NGRAMS_VALUE, 2);
        store.setDefault(IChangeDistillerPreferenceConstants.LEAF_STRING_SIM_THRESHOLD, fLeafStringSimilarityThreshold);
        store.setDefault(IChangeDistillerPreferenceConstants.NODE_STRING_SIM_ENABLEMENT, false);
        store.setDefault(IChangeDistillerPreferenceConstants.DYNAMIC_THRESHOLD_ENABLEMENT, true);
        store.setDefault(IChangeDistillerPreferenceConstants.DEPTH_VALUE, fDynamicThresholdDepthValue);
        store.setDefault(IChangeDistillerPreferenceConstants.DYNAMIC_THRESHOLD, fDynamicThreshold);
        store.setDefault(
                IChangeDistillerPreferenceConstants.NODE_SIM,
                IChangeDistillerPreferenceConstants.PREF_NODE_SIM_CHAWATHE);
        store.setDefault(IChangeDistillerPreferenceConstants.NODE_SIM_THRESHOLD, fNodeSimilarityThreshold);
        store.setDefault(
                IChangeDistillerPreferenceConstants.LEAF_MATCHING,
                IChangeDistillerPreferenceConstants.PREF_LEAF_MATCHING_BEST);
    }

}
