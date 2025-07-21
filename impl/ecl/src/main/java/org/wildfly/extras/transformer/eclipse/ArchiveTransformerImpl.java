/*
 * Copyright 2020 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extras.transformer.eclipse;

import static org.slf4j.helpers.NOPLogger.NOP_LOGGER;

import org.eclipse.transformer.TransformOptions;
import org.eclipse.transformer.action.Changes;
import org.eclipse.transformer.Transformer;
import org.wildfly.extras.transformer.ArchiveTransformer;

import java.io.File;
import java.io.IOException;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class ArchiveTransformerImpl extends ArchiveTransformer {

    ArchiveTransformerImpl(final File configsDir, final boolean verbose, final boolean invert) {
        super(configsDir, verbose, invert);
    }

    @Override
    public boolean transform(final File inJarFile, final File outJarFile) {
        boolean transformed;
        try {
            if (!verbose) {
                // Disable all logging that is very verbose.
                System.setProperty("org.slf4j.simpleLogger.log.Transformer", "error");
            }
            TransformOptions transformOptions = new BataviaTransformOptions(configsDir, verbose, invert, inJarFile, outJarFile);
            transformed = transform(transformOptions, true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return transformed;
    }

    private boolean transform(TransformOptions transformOptions, boolean silent) throws IOException {
        Transformer jTrans;
        if (silent) {
            jTrans = new Transformer(NOP_LOGGER, transformOptions);
        } else {
            jTrans = new Transformer(transformOptions);
        }

        @SuppressWarnings("unused")
        Transformer.ResultCode rc = jTrans.run();
        if (rc != Transformer.ResultCode.SUCCESS_RC) {
            throw new IOException("Error occurred during transformation. Error code " + rc);
        }
        // New API needed in eclipse transformer.
        Changes changes = jTrans.getLastActiveChanges();
        if (changes != null) {
            return changes.isChanged();
        }
        return false;
    }

    @Override
    public boolean canTransformIndividualClassFile() {
        return true;
    }

}
