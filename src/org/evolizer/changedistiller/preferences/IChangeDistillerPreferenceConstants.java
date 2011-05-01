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

/**
 * Constants for ChangeDistiller preference page
 * 
 * @author fluri
 * 
 */
public interface IChangeDistillerPreferenceConstants {

    String PREF_STRING_SIM_LEVENSHTEIN = "levenshtein";
    String PREF_STRING_SIM_NGRAMS = "ngrams";
    String PREF_NODE_SIM_CHAWATHE = "chawathe";
    String PREF_NODE_SIM_DICE = "dice";
    String PREF_LEAF_MATCHING_FIRST = "first_match";
    String PREF_LEAF_MATCHING_BEST = "best_match";

    String LEAF_STRING_SIM = "leaf_string_sim";
    String LEAF_NGRAMS_VALUE = "leaf_ngrams_value";
    String LEAF_STRING_SIM_THRESHOLD = "leaf_string_sim_threshold";
    String NODE_STRING_SIM_ENABLEMENT = "node_string_sim_enablement";
    String NODE_STRING_SIM = "node_string_sim";
    String NODE_STRING_SIM_THRESHOLD = "node_string_sim_threshold";
    String NODE_SIM = "node_sim";
    String NODE_SIM_THRESHOLD = "node_sim_threshold";
    String DYNAMIC_THRESHOLD_ENABLEMENT = "dynamic_threshold_enablement";
    String DEPTH_VALUE = "depth_value";
    String DYNAMIC_THRESHOLD = "dynamic_threshold";
    String LEAF_MATCHING = "leaf_matching";
    String NODE_STRING_SIM_NGRAMS_VALUE = "node_string_sim_ngrams_value";
}
