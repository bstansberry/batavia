/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extras.transformer.eclipse;

import static org.eclipse.transformer.AppOption.RULES_DIRECT;
import static org.eclipse.transformer.AppOption.RULES_MASTER_TEXT;
import static org.eclipse.transformer.AppOption.RULES_PER_CLASS_CONSTANT;
import static org.eclipse.transformer.AppOption.RULES_RENAMES;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.transformer.AppOption;
import org.eclipse.transformer.TransformOptions;
import org.eclipse.transformer.jakarta.JakartaTransform;

final class BataviaTransformOptions implements TransformOptions {


    public static final String DEFAULT_RENAMES_REFERENCE = "jakarta-renames.properties";
    public static final String DEFAULT_MASTER_TXT_REFERENCE = "jakarta-txt-master.properties";
    public static final String DEFAULT_PER_CLASS_REFERENCE = "jakarta-per-class.properties";
    public static final String DEFAULT_DIRECT_REFERENCE = "jakarta-direct.properties";
    private static final Map<AppOption, String> DEFAULT_APP_OPTIONS =
            Map.of(
                    RULES_RENAMES, DEFAULT_RENAMES_REFERENCE,
                    RULES_MASTER_TEXT, DEFAULT_MASTER_TXT_REFERENCE,
                    RULES_PER_CLASS_CONSTANT, DEFAULT_PER_CLASS_REFERENCE,
                    RULES_DIRECT, DEFAULT_DIRECT_REFERENCE
            );

    private final Map<AppOption, String> configDirOptions = new HashMap<>();
    private final File inJarFile;
    private final File outJarFile;

    BataviaTransformOptions(File configsDir, boolean verbose, boolean invert, File inJarFile, File outJarFile) {
        if (configsDir != null) {
            File configFile = new File(configsDir, DEFAULT_RENAMES_REFERENCE);
            if (configFile.exists() && configFile.isFile()) {
                configDirOptions.put(RULES_RENAMES, configFile.getAbsolutePath());
            }
            configFile = new File(configsDir, DEFAULT_MASTER_TXT_REFERENCE);
            if (configFile.exists() && configFile.isFile()) {
                configDirOptions.put(RULES_MASTER_TEXT, configFile.getAbsolutePath());
            }
            configFile = new File(configsDir, DEFAULT_PER_CLASS_REFERENCE);
            if (configFile.exists() && configFile.isFile()) {
                configDirOptions.put(RULES_PER_CLASS_CONSTANT, configFile.getAbsolutePath());
            }
            configFile = new File(configsDir, DEFAULT_DIRECT_REFERENCE);
            if (configFile.exists() && configFile.isFile()) {
                configDirOptions.put(RULES_DIRECT, configFile.getAbsolutePath());
            }
        }
        if (verbose) {
            configDirOptions.put(AppOption.LOG_DEBUG, "true");
        }
        if (invert) {
            configDirOptions.put(AppOption.INVERT, "true");
        }

        this.inJarFile = inJarFile;
        this.outJarFile = outJarFile;
    }

    @Override
    public boolean hasOption(AppOption option) {
        return configDirOptions.containsKey(option);
    }

    @Override
    public String getOptionValue(AppOption option) {
        return configDirOptions.get(option);
    }

    @Override
    public List<String> getOptionValues(AppOption option) {
        if (hasOption(option)) {
            return List.of(getOptionValue(option));
        }
        return null;
    }

    @Override
    public Function<String, URL> getRuleLoader() {
        return BataviaTransformOptions::getResource;
    }

    @Override
    public String getDefaultValue(AppOption option) {
        return DEFAULT_APP_OPTIONS.get(option);
    }

    @Override
    public String getInputFileName() {
        return inJarFile.getAbsolutePath();
    }

    /**
     * Returns the output file for the transformation.
     *
     * @return The output file for the transformation.
     */
    @Override
    public String getOutputFileName() {

        return outJarFile.getAbsolutePath();
    }

    private static URL getResource(String name) {
        URL result = BataviaTransformOptions.class.getResource(name);
        return result == null ? JakartaTransform.class.getResource(name) : result;
    }
}
