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
package org.evolizer.changedistiller.treedifferencing.matching.measure;

import simpack.accessor.string.StringAccessor;
import simpack.measure.sequence.Levenshtein;

/**
 * Implementation of the Levenshtein similarity measure.
 * 
 * @author fluri
 * 
 */
public class LevenshteinCalculator implements IStringSimilarityCalculator {

    /**
     * {@inheritDoc}
     */
    public double calculateSimilarity(String left, String right) {
        if (left.equals("") && right.equals("")) {
            return 1.0;
        }
        Levenshtein<String> lm = new Levenshtein<String>(new StringAccessor(left), new StringAccessor(right));
        return lm.getSimilarity();
    }

}
