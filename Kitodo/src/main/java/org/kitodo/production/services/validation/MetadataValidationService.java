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

package org.kitodo.production.services.validation;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataformat.mets.MetsXmlElementAccessInterface;
import org.kitodo.api.validation.State;
import org.kitodo.api.validation.ValidationResult;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.production.dto.ProcessDTO;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyMetsModsDigitalDocumentHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyPrefsHelper;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ProcessService;
import org.kitodo.production.services.data.RulesetService;
import org.kitodo.production.services.data.UserService;
import org.kitodo.production.services.file.FileService;

public class MetadataValidationService {
    private static final Logger logger = LogManager.getLogger(MetadataValidationService.class);

    /**
     * Message key if the identifier contains invalid characters.
     */
    private static final String MESSAGE_IDENTIFIER_INVALID = "invalidIdentifierCharacter";

    /**
     * Message key if the value for the identifier is missing.
     */
    private static final String MESSAGE_IDENTIFIER_MISSING = "metadataMissingIdentifier";

    /**
     * Message key if the value of the identifier contains the same value in
     * different places.
     */
    private static final String MESSAGE_IDENTIFIER_NOT_UNIQUE = "invalidIdentifierSame";

    /**
     * Message key if no media is assigned.
     */
    private static final String MESSAGE_MEDIA_MISSING = "metadataMediaError";

    /**
     * Message key if media is present but not assigned to a structure.
     */
    private static final String MESSAGE_MEDIA_UNASSIGNED = "metadataMediaUnassigned";

    /**
     * Message key if a structure has no media assigned.
     */
    private static final String MESSAGE_STRUCTURE_WITHOUT_MEDIA = "metadataStructureWithoutMedia";

    /**
     * Message key if the input is invalid.
     */
    private static final String MESSAGE_VALUE_INVALID = "metadataInvalidData";

    /**
     * Message key if the input is missing.
     */
    private static final String MESSAGE_VALUE_MISSING = "metadataMandatoryElement";

    /**
     * Message key if there are too many entries of a type.
     */
    private static final String MESSAGE_VALUE_TOO_OFTEN = "metadataNotOneElement";

    /**
     * Message key if there are too little entries of a type.
     */
    private static final String MESSAGE_VALUE_TOO_RARE = "metadataNotEnoughElements";

    private final ProcessService processService = ServiceManager.getProcessService();
    private final RulesetService rulesetService = ServiceManager.getRulesetService();
    private final UserService userService = ServiceManager.getUserService();

    /**
     * Validate.
     *
     * @param process
     *            object
     * @return boolean
     * @deprecated This validation is a work-around to keep legacy code
     *             functional. It should not be used anymore.
     */
    @Deprecated
    public boolean validate(Process process) {
        LegacyPrefsHelper prefs = rulesetService.getPreferences(process.getRuleset());
        LegacyMetsModsDigitalDocumentHelper gdzfile;
        try {
            gdzfile = processService.readMetadataFile(process);
        } catch (IOException | RuntimeException e) {
            Helper.setErrorMessage("metadataReadError", new Object[] {process.getTitle() }, logger, e);
            return false;
        }
        return validate(gdzfile, prefs, process);
    }

    /**
     * Validate.
     *
     * @param gdzfile
     *            Fileformat object
     * @param prefs
     *            Prefs object
     * @param process
     *            object
     * @return boolean
     * @deprecated This validation is a work-around to keep legacy code
     *             functional. It should not be used anymore.
     */
    @Deprecated
    public boolean validate(LegacyMetsModsDigitalDocumentHelper gdzfile, LegacyPrefsHelper prefs, Process process) {
        try {
            return !State.ERROR.equals(validate(gdzfile.getWorkpiece(), prefs.getRuleset()).getState());
        } catch (DataException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public ValidationResult validate(URI metsFileUri, URI rulesetFileUri) {
        return ServiceManager.getMetadataValidationService().validate(metsFileUri, FileService.getCurrentLockingUser(),
            rulesetFileUri, getMetadataLanguage(), getTranslations());
    }

    /**
     * Validates a workpiece based on a rule set.
     * 
     * @param workpiece
     *            METS file
     * @param ruleset
     *            Ruleset file
     * @return the validation result
     * @throws DataException
     *             if an error occurs while reading from the search engine
     */
    private ValidationResult validate(MetsXmlElementAccessInterface workpiece, RulesetManagementInterface ruleset)
            throws DataException {

        Collection<ValidationResult> results = new ArrayList<>();
        results.add(checkTheIdentifier(workpiece));
        results.add(ServiceManager.getMetadataValidationService().validate(workpiece, ruleset, getMetadataLanguage(),
            getTranslations()));
        return merge(results);
    }

    /**
     * Verifies that the rules for the identifier are met.
     * 
     * @param workpiece
     *            METS file
     * @return the validation result
     * @throws DataException
     *             if an error occurs while reading from the search engine
     */
    private ValidationResult checkTheIdentifier(MetsXmlElementAccessInterface workpiece) throws DataException {
        boolean error = false;
        boolean warning = false;
        Collection<String> messages = new HashSet<>();

        String workpieceId = workpiece.getId();
        if (Objects.isNull(workpieceId)) {
            messages.add(Helper.getTranslation(MESSAGE_IDENTIFIER_MISSING));
            warning = true;
        } else {
            if (!Pattern.matches(ConfigCore.getParameterOrDefaultValue(ParameterCore.VALIDATE_IDENTIFIER_REGEX),
                workpieceId)) {
                messages.add(Helper.getTranslation(MESSAGE_IDENTIFIER_INVALID, Arrays.asList(workpieceId)));
                error = true;
            }
            List<ProcessDTO> processDTOs = processService.findAll().parallelStream()
                    .filter(processDTO -> workpieceId.equals(String.valueOf(processDTO.getId())))
                    .collect(Collectors.toList());
            if (processDTOs.size() > 1) {
                messages.add(Helper.getTranslation(MESSAGE_IDENTIFIER_NOT_UNIQUE,
                    Arrays.asList(workpieceId, processDTOs.get(0).getTitle(), processDTOs.get(1).getTitle())));
                error = true;
            }
        }

        return new ValidationResult(error ? State.ERROR : warning ? State.WARNING : State.SUCCESS, messages);
    }

    // helper methods

    /**
     * Returns the meta-data language for a user.
     * 
     * @return the meta-data language
     */
    private List<LanguageRange> getMetadataLanguage() {
        User user = userService.getAuthenticatedUser();
        String metadataLanguage = user != null ? user.getMetadataLanguage()
                : Helper.getRequestParameter("Accept-Language");
        return LanguageRange.parse(metadataLanguage != null ? metadataLanguage : "en");
    }

    private Map<String, String> getTranslations() {
        Map<String, String> translations = new HashMap<>();
        translations.put(MESSAGE_MEDIA_MISSING, Helper.getTranslation(MESSAGE_MEDIA_MISSING));
        translations.put(MESSAGE_MEDIA_UNASSIGNED, Helper.getTranslation(MESSAGE_MEDIA_UNASSIGNED));
        translations.put(MESSAGE_STRUCTURE_WITHOUT_MEDIA, Helper.getTranslation(MESSAGE_STRUCTURE_WITHOUT_MEDIA));
        translations.put(MESSAGE_VALUE_INVALID, Helper.getTranslation(MESSAGE_VALUE_INVALID));
        translations.put(MESSAGE_VALUE_MISSING, Helper.getTranslation(MESSAGE_VALUE_MISSING));
        translations.put(MESSAGE_VALUE_TOO_OFTEN, Helper.getTranslation(MESSAGE_VALUE_TOO_OFTEN));
        translations.put(MESSAGE_VALUE_TOO_RARE, Helper.getTranslation(MESSAGE_VALUE_TOO_RARE));
        return translations;
    }

    /**
     * Merges several individual validation results into one validation result.
     * 
     * @param results
     *            individual validation results
     * @return merged validation result
     */
    private static ValidationResult merge(Collection<ValidationResult> results) {
        boolean error = false;
        boolean warning = false;
        Collection<String> messages = new HashSet<>();

        for (ValidationResult result : results) {
            if (result.getState().equals(State.ERROR)) {
                error = true;
            } else if (result.getState().equals(State.WARNING)) {
                warning = true;
            }
            messages.addAll(result.getResultMessages());
        }

        return new ValidationResult(error ? State.ERROR : warning ? State.WARNING : State.SUCCESS, messages);
    }
}
