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

import org.eclipse.osgi.util.NLS;

/**
 * Messages is the class that handles the messages.
 * 
 * @author fluri
 */
public class Messages extends NLS {

    public static String sPreferencePageDescription;
    public static String sLeafStringSim;
    public static String sNGramsValue;
    public static String sLeafStringSimThreshold;
    public static String sNodeStringSimEnablement;
    public static String sNodeStringSimGroup;
    public static String sNodeStringSim;
    public static String sNodeStringSimThreshold;
    public static String sNodeSim;
    public static String sNodeSimThreshold;
    public static String sDynamicThresholdEnablement;
    public static String sDepthValue;
    public static String sDynamicThreshold;
    public static String sLeafMatching;
    public static String sStringSimLevenshtein;
    public static String sStringSimNGrams;
    public static String sNodeSimChawathe;
    public static String sNodeSimDice;
    public static String sLeafMatchingFirst;
    public static String sLeafMatchingBest;

    private static final String BUNDLE_NAME = "org.evolizer.changedistiller.preferences.messages"; //$NON-NLS-1$

    static {
        // load message values from bundle file
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }
}
