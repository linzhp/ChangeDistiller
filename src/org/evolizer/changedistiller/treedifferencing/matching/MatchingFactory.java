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
package org.evolizer.changedistiller.treedifferencing.matching;

import java.util.Set;

import org.eclipse.jface.preference.IPreferenceStore;
import org.evolizer.changedistiller.ChangeDistillerPlugin;
import org.evolizer.changedistiller.preferences.IChangeDistillerPreferenceConstants;
import org.evolizer.changedistiller.treedifferencing.ITreeMatcher;
import org.evolizer.changedistiller.treedifferencing.NodePair;
import org.evolizer.changedistiller.treedifferencing.matching.measure.ChawatheCalculator;
import org.evolizer.changedistiller.treedifferencing.matching.measure.DiceNodeSimilarity;
import org.evolizer.changedistiller.treedifferencing.matching.measure.INodeSimilarityCalculator;
import org.evolizer.changedistiller.treedifferencing.matching.measure.IStringSimilarityCalculator;
import org.evolizer.changedistiller.treedifferencing.matching.measure.LevenshteinCalculator;
import org.evolizer.changedistiller.treedifferencing.matching.measure.NGramsCalculator;

/**
 * Factory to generate a {@link ITreeMatcher} out of specified preference values.
 * 
 * @author fluri
 * @see ITreeMatcher
 * @see BestLeafTreeMatcher
 * @see DefaultTreeMatcher
 */
public final class MatchingFactory {

    private MatchingFactory() {}

    /**
     * Returns an {@link ITreeMatcher} according to specified preference values.
     * 
     * @param matchingSet
     *            in which the matcher stores the match pairs
     * @return the tree matcher out of specified preference values
     */
    public static ITreeMatcher getMatcher(Set<NodePair> matchingSet) {

        // leaf matching
        String leaf = IChangeDistillerPreferenceConstants.PREF_STRING_SIM_NGRAMS;
        IStringSimilarityCalculator leafCalc = getStringSimilarityMeasure(null, leaf);
        if (leafCalc instanceof NGramsCalculator) {
            ((NGramsCalculator) leafCalc).setN(2);
        }

        double lTh = 0.6;

        // node string matching
        IStringSimilarityCalculator nodeStringCalc = leafCalc;
        double nStTh = lTh;
//        if (store.getBoolean(IChangeDistillerPreferenceConstants.NODE_STRING_SIM_ENABLEMENT)) {
//            String nodeString = store.getString(IChangeDistillerPreferenceConstants.NODE_STRING_SIM);
//            nodeStringCalc = getStringSimilarityMeasure(store, nodeString);
//            if (nodeStringCalc instanceof NGramsCalculator) {
//                ((NGramsCalculator) nodeStringCalc).setN(store
//                        .getInt(IChangeDistillerPreferenceConstants.NODE_STRING_SIM_NGRAMS_VALUE));
//            }
//
//            nStTh = store.getDouble(IChangeDistillerPreferenceConstants.NODE_STRING_SIM_THRESHOLD);
//        }

        // node matching
        INodeSimilarityCalculator nodeCalc = null;
        nodeCalc = new ChawatheCalculator();
        nodeCalc.setLeafMatchSet(matchingSet);

        double nTh = 0.6;

        // best match
        ITreeMatcher result = null;
        result = new BestLeafTreeMatcher();
        result.init(leafCalc, lTh, nodeStringCalc, nStTh, nodeCalc, nTh);

        // dynamic threshold
        result.enableDynamicThreshold(4, 0.4);
        result.setMatchingSet(matchingSet);
        return result;
    }

    private static IStringSimilarityCalculator getStringSimilarityMeasure(IPreferenceStore store, String calc) {
        IStringSimilarityCalculator leafCalc;
        if (calc.equals(IChangeDistillerPreferenceConstants.PREF_STRING_SIM_LEVENSHTEIN)) {
            leafCalc = new LevenshteinCalculator();
        } else if (calc.equals(IChangeDistillerPreferenceConstants.PREF_STRING_SIM_NGRAMS)) {
            leafCalc = new NGramsCalculator();
        } else {
            leafCalc = new LevenshteinCalculator();
        }
        return leafCalc;
    }

}
