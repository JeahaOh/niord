/*
 * Copyright 2016 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.core.publication.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.model.publication.PublicationTypeVo;
import org.niord.core.util.JsonUtils;

import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads publications from a publication-type.json file.
 * <p>
 * Please note, the actual publication-type-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the PublicationTypeVo class. Example:
 * <pre>
 * [
 *   {
 *      "typeId": "dk-dma-publications",
 *      "priority": 50,
 *      "publish": true,
 *      "descs": [
 *         {
 *            "lang": "da",
 *            "name": "Søfartsstyrelsens publikationer"
 *         },
 *         {
 *            "lang": "en",
 *            "name": "Danish Maritime Authority publications"
 *         }
 *      ]
 *   },
 *   {
 *       etc, etc
 *   }
 * ]
 * </pre>
 */
@Named
public class BatchPublicationTypeImportReader extends AbstractItemHandler {

    List<PublicationTypeVo> publicationTypes;
    int publicationTypeNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the publications from the file
        publicationTypes = JsonUtils.readJson(
                new TypeReference<List<PublicationTypeVo>>(){},
                path);

        if (prevCheckpointInfo != null) {
            publicationTypeNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + publicationTypes.size() + " publication  types from index " + publicationTypeNo);
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (publicationTypeNo < publicationTypes.size()) {
            getLog().info("Reading publication type no " + publicationTypeNo);
            return publicationTypes.get(publicationTypeNo++);
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return publicationTypeNo;
    }
}
