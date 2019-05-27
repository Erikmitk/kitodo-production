/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.production.helper.metadata.legacytypeimplementations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataformat.MediaUnit;

/**
 * Connects a legacy file set its corresponding doc struct to a media units
 * list. This is a soldering class to keep legacy code operational which is
 * about to be removed. Do not use this class.
 */

public class LegacyFileSetDocStructHelper implements LegacyDocStructHelperInterface {
    private static final Logger logger = LogManager.getLogger(LegacyFileSetDocStructHelper.class);

    /**
     * The media units list accessed via this soldering class.
     */
    private List<MediaUnit> mediaUnits;

    @Deprecated
    public LegacyFileSetDocStructHelper(List<MediaUnit> mediaUnits) {
        this.mediaUnits = mediaUnits;
    }

    @Deprecated
    public Iterable<LegacyContentFileHelper> getAllFiles() {
        // TODO remove
        throw LegacyDocStructHelperInterface.andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    @Deprecated
    public void addMetadata(LegacyMetadataHelper metadata) {
        /*
         * Legacy code tries to add (empty) meta-data entries here. I guess this
         * is a bug.
         */
    }

    @Override
    @Deprecated
    public List<LegacyDocStructHelperInterface> getAllChildren() {
        List<LegacyDocStructHelperInterface> result = new ArrayList<>(mediaUnits.size());
        for (MediaUnit mediaUnit : mediaUnits) {
            result.add(new LegacyInnerPhysicalDocStructHelper(mediaUnit));
        }
        return result;
    }

    @Override
    @Deprecated
    public List<LegacyDocStructHelperInterface> getAllChildrenByTypeAndMetadataType(String page, String asterisk) {
        List<LegacyDocStructHelperInterface> result = new ArrayList<>(mediaUnits.size());
        for (MediaUnit mediaUnit : mediaUnits) {
            result.add(new LegacyInnerPhysicalDocStructHelper(mediaUnit));
        }
        return result;
    }

    @Override
    @Deprecated
    public List<LegacyMetadataHelper> getAllMetadata() {
        return Collections.emptyList();
    }

    @Override
    @Deprecated
    public List<LegacyMetadataHelper> getAllMetadataByType(LegacyMetadataTypeHelper metadataType) {
        return Collections.emptyList();
    }
}
