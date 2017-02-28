/**
 * Copyright (C) 2010-2016 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser Public License as published by the
 * Free Software Foundation, either version 3.0 of the License, or (at your
 * option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License along
 * with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */


package org.evosuite.setup;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.evosuite.Properties;
import org.evosuite.utils.FileIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides identification of additional classes.
 * Serves as a singleton.
 *
 * @author Benjamin Graf
 */
public class AdditionalClasses {

    private static Logger logger = LoggerFactory.getLogger(AdditionalClasses.class);

    /**
     * Set of additional classes to instrument.
     */
    private Set<String> additionalClasses;

    /**
     * Setup
     */
    private AdditionalClasses() {
        additionalClasses = new HashSet<>();

        // Read additional classes from file if
        if (Properties.ADDITIONAL_CLASSES.equals("")) {
            logger.debug("No additional classes");
            return;
        }

        File file = new File(Properties.ADDITIONAL_CLASSES);
        try {
            List<String> lines = FileUtils.readLines(file);

            if (lines.size() == 0) {
                logger.warn("Additional classes file is empty");
            }

            for (String line : lines) {
                line = line.trim();
                if (line.length() > 0) {
                    logger.info("Adding additional class: " + line);
                    additionalClasses.add(line);
                }
            }
        } catch (IOException e) {
            logger.error("Could not read additional classes from: " + Properties.ADDITIONAL_CLASSES);
            return;
        }
    }

    private Set<String> getAdditionalClassesImpl() { return additionalClasses; }

    public static Set<String> getAdditionalClasses() {
        return getInstance().getAdditionalClassesImpl();
    }


    private boolean limitToCutImpl() {
        return additionalClasses.size() == 0;
    }

    private boolean shouldInstrumentImpl(String className) {
        return additionalClasses.contains(className);
    }

    /**
     * Return whether branching goals should be limited to CUT.
     * @return  <tt>true</tt> if there are additional classes, <tt>false</tt> otherwise.
     */
    public static boolean limitToCut() {
        return getInstance().limitToCutImpl();
    }

    /**
     * Returns true iff the given classname is contained in the set of additional classes.
     * @param className     The classname.
     * @return              Whether the class should be instrumented.
     */
    public static boolean shouldInstrument(String className) {
        return getInstance().shouldInstrumentImpl(className);
    }

    /**
     * Singleton instance.
     */
    static private AdditionalClasses instance;

    /**
     * Singleton access method.
     * @return The instance.
     */
    static private AdditionalClasses getInstance() {
        if (instance == null) {
            instance = new AdditionalClasses();
        }
        return instance;
    }
}
