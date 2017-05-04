/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.mailinglist;

import freemarker.template.TemplateException;
import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.dictionary.DictionaryService;
import org.niord.core.dictionary.vo.DictionaryVo;
import org.niord.core.mail.IMailable;
import org.niord.core.mail.ScheduledMail;
import org.niord.core.mail.ScheduledMailRecipient;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageTokenExpander;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.model.DescEntity;
import org.niord.core.script.FmTemplateService;
import org.niord.core.script.JsResourceService;
import org.niord.core.script.ScriptResource;
import org.niord.core.service.BaseService;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.ejb.Asynchronous;
import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.niord.core.mail.ScheduledMailRecipient.RecipientType.TO;
import static org.niord.core.script.ScriptResource.Type.FM;

/**
 * Handles execution of mailing list triggers
 */
@Stateless
public class MailingListExecutionService extends BaseService {

    @Inject
    Logger log;

    @Inject
    NiordApp app;

    @Inject
    MessageService messageService;

    @Inject
    FmTemplateService templateService;

    @Inject
    JsResourceService javaScriptService;

    @Inject
    MailingListService mailingListService;

    @Inject
    DictionaryService dictionaryService;


    /***************************************/
    /** Status change triggers            **/
    /***************************************/


    /**
     * Handle mailing list execution for the message. Called from the MailingListMessageListener MDB listener.
     * @param messageUid the UID of the message
     */
    @Asynchronous
    public void checkStatusChangeMailingListExecution(String messageUid) {

        long t0 = System.currentTimeMillis();

        Message message = messageService.findByUid(messageUid);

        List<MailingListTrigger> triggers = mailingListService.findStatusChangeTriggers(message.getStatus());
        log.info(String.format("Found %d status change listeners for %s in status %s",
                triggers.size(),
                messageUid,
                message.getStatus()));

        for (MailingListTrigger trigger : triggers) {
            try {
                executeStatusChangeTrigger(trigger, message, true);
            } catch (Exception e) {
                log.error("Error executing status-change mailing-list trigger " + trigger.getId(), e);
            }
        }
        log.info(String.format("Executed %d status change listeners for %s in status %s in %d ms",
                triggers.size(),
                messageUid,
                message.getStatus(),
                System.currentTimeMillis() - t0));
    }


    /**
     * Executes the status change mailing list trigger for the given message
     * @param trigger the status change mailing list trigger to execute
     * @param message the message to execute the trigger for
     * @param persist whether to persist the mails or not
     */
    public List<ScheduledMail> executeStatusChangeTrigger(MailingListTrigger trigger, Message message, boolean persist) throws Exception {

        if (trigger.getType() != TriggerType.STATUS_CHANGE) {
            throw new IllegalAccessException("Must be a status-change trigger");
        }

        List<ScheduledMail> mails = new ArrayList<>();

        // Check that one or more language variants have been defined
        List<String> languages = trigger.getDescs().stream()
                .map(DescEntity::getLang)
                .collect(Collectors.toList());
        if (languages.isEmpty()) {
            return mails;
        }

        // In no Freemarker script resources have been defined, bail
        if (trigger.getScriptResourcePaths().isEmpty() ||
                trigger.getScriptResourcePaths().stream().noneMatch(p -> ScriptResource.path2type(p) == FM)) {
            log.debug("No Freemarker template defined for trigger " + trigger.getId());
            return mails;
        }

        // If a message filter is defined for the trigger, see if the message qualifies
        if (StringUtils.isNotBlank(trigger.getMessageFilter())) {
            MessageFilter filter = MessageFilter.getInstance(trigger.getMessageFilter());
            if (!filter.matches(message)) {
                log.debug(String.format("Message %s not matching trigger message filter: %s",
                        message.getUid(),
                        trigger.getMessageFilter()));
                return mails;
            }
        }


        // Create mails language by language
        for (String language : languages) {
            ScheduledMail mail = createMailTemplate(trigger, language, languages, message);

            // First check if there are any recipients for the language
            if (mail.getRecipients().isEmpty()) {
                continue;
            }

            String html = executeScriptResources(trigger, Collections.singletonList(message), language);
            mail.setHtmlContents(html);

            // All a copy of the mail for each recipient
            mails.addAll(mail.splitByRecipient());
        }

        // Persist the mails
        if (persist) {
            persistMails(mails);
        }
        return mails;
    }


    /***************************************/
    /** Scheduled triggers                **/
    /***************************************/


    /**
     * Checks every minute if there are pending scheduled mailing list triggers.
     *
     * TODO: Transaction Handling - one failed transaction should not prevent all future executions.
     */
    @Schedule(persistent=false, second="37", minute="*/1", hour="*/1")
    public void checkExecuteScheduledTriggers() {

        List<MailingListTrigger> pendingTriggers = mailingListService.findPendingScheduledTriggers();

        if (!pendingTriggers.isEmpty()) {
            log.info("Processing " + pendingTriggers.size() + " scheduled triggers");
            for (MailingListTrigger trigger : pendingTriggers) {
                try {
                    executeScheduledTrigger(trigger, true);
                    trigger.checkComputeNextScheduledExecution();
                    saveEntity(trigger);
                } catch (Exception e) {
                    log.error("Failed executing scheduled mailing list trigger " + trigger + ": " + e);
                }
            }
        }
    }


    /**
     * Executes the scheduled mailing list trigger for the given message
     * @param trigger the scheduled mailing list trigger to execute
     * @param persist whether to persist the mails or not
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public List<ScheduledMail> executeScheduledTrigger(MailingListTrigger trigger, boolean persist) throws Exception {

        if (trigger.getType() != TriggerType.SCHEDULED) {
            throw new IllegalAccessException("Must be a scheduled trigger");
        }

        List<ScheduledMail> mails = new ArrayList<>();

        // Check that one or more language variants have been defined
        List<String> languages = trigger.getDescs().stream()
                .map(DescEntity::getLang)
                .collect(Collectors.toList());
        if (languages.isEmpty()) {
            return mails;
        }

        // In no Freemarker script resources have been defined, bail
        if (trigger.getScriptResourcePaths().isEmpty() ||
                trigger.getScriptResourcePaths().stream().noneMatch(p -> ScriptResource.path2type(p) == FM)) {
            log.debug("No Freemarker template defined for trigger " + trigger.getId());
            return mails;
        }

        // If no message query is defined for the trigger
        if (StringUtils.isBlank(trigger.getMessageQuery())) {
            log.debug("No mail query defined for trigger " + trigger.getId());
            return mails;
        }

        // Perform the message search
        MessageSearchParams params = MessageSearchParams.instantiate(null, trigger.getMessageQuery());
        params.maxSize(Integer.MAX_VALUE);
        PagedSearchResultVo<Message> messageResult = messageService.search(params);

        // Create mails language by language
        for (String language : languages) {
            ScheduledMail mail = createMailTemplate(trigger, language, languages, null);

            // First check if there are any recipients for the language
            if (mail.getRecipients().isEmpty()) {
                continue;
            }

            String html = executeScriptResources(trigger, messageResult.getData(), language);
            mail.setHtmlContents(html);

            // All a copy of the mail for each recipient
            mails.addAll(mail.splitByRecipient());
        }

        // Persist the mails
        if (persist) {
            persistMails(mails);
        }
        return mails;
    }


    /***************************************/
    /** Common for all trigger types      **/
    /***************************************/


    /**
     * Creates a template mail for the mailing list trigger, with instantiated recipients and subject
     *
     * @param trigger the mailing list trigger to create a mail template for
     * @param language the current language
     * @param languages all language versions
     * @param message optionally, a message when the mails is created for a single message
     * @return the mail template
     **/
    private ScheduledMail createMailTemplate(MailingListTrigger trigger, String language, List<String> languages, Message message) {

        // Compute the default language
        String defaultLanguage = app.getLanguage("en");
        if (languages.size() == 1 || (languages.size() > 1 && !languages.contains(defaultLanguage))) {
            defaultLanguage = app.getLanguage(languages.get(0));
        }
        boolean isDefaultLanguage = defaultLanguage.equals(language);

        ScheduledMail mail = new ScheduledMail();

        // Create the mail subject
        MailingListTriggerDesc desc = trigger.getDesc(language);
        String subject = StringUtils.defaultString(desc.getSubject());

        // Check if we need to replace any tokens in the subject, like "${short-id}", "${title}", etc.
        subject = expandSubject(message, language, languages, subject);
        mail.setSubject(subject);

        // Get all recipients matching the given language
        List<IMailable> recipients = new ArrayList<>();
        recipients.addAll(trigger.getMailingList().getUsers().stream()
                .filter(r -> (r.getLanguage() == null && isDefaultLanguage) || language.equalsIgnoreCase(r.getLanguage()))
                .collect(Collectors.toList()));
        recipients.addAll(trigger.getMailingList().getContacts().stream()
                .filter(r -> (r.getLanguage() == null && isDefaultLanguage) || language.equalsIgnoreCase(r.getLanguage()))
                .collect(Collectors.toList()));

        recipients.forEach(r -> mail.addRecipient(new ScheduledMailRecipient(TO, r.computeInternetAddress())));

        return mail;
    }


    /** Check if we need to replace any tokens in the subject, like "${short-id}", "${title}", etc. **/
    private String expandSubject(Message message, String language, List<String> languages, String subject) {

        DictionaryVo mailDict = dictionaryService.getCachedDictionary("mail");
        String shortFormat = mailDict.value(language, "mail.date_format.short");
        String longFormat = mailDict.value(language, "mail.date_format.long");
        Date today = new Date();

        return MessageTokenExpander.getInstance(message, languages, language)
                .token("${date-short}", new SimpleDateFormat(shortFormat).format(today))
                .token("${date-long}", new SimpleDateFormat(longFormat).format(today))
                .expandTokens(subject);
    }


    /** Saves the list of scheduled mail templates **/
    private void persistMails(List<ScheduledMail> mails) {
        for (int x = 0; x < mails.size(); x++) {
            saveEntity(mails.get(x));
            if ((x + 1) % 20 == 0) {
                em.flush();
            }
        }
    }


    /***************************************/
    /** Template Execution                **/
    /***************************************/


    /**
     * Executes the script resources associated with a mailing list trigger and return the resulting HTML.
     * <p>
     * The list of script resources should be defined by an optional list of JavaScript resources
     * and a single freemarker template that actually generates the HTML for the mail.
     *
     * @param trigger the mailing list trigger to execute
     * @param messages  the messages to apply the template to
     * @param language the language of the template execution
     * @return the resulting html generated in the templates
     */
    public String executeScriptResources(MailingListTrigger trigger, List<Message> messages, String language) throws Exception {

        // Sanity check
        if (trigger.getScriptResourcePaths().isEmpty() || messages.isEmpty()) {
            return null;
        }

        // Adjust the message prior to executing the template
        List<SystemMessageVo> msgs = messages.stream()
                .map(m -> m.toVo(SystemMessageVo.class, Message.MESSAGE_DETAILS_AND_PROMULGATIONS_FILTER))
                .collect(Collectors.toList());
        msgs.forEach(m -> m.sort(language));

        // Create context data to use with Freemarker templates and JavaScript updates
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("languages", app.getLanguages());
        contextData.put("language", language);
        contextData.put("messages", messages);
        if (msgs.size() == 1) {
            contextData.put("message", msgs.get(0));
        }
        contextData.put("params", new HashMap<>());

        StringBuilder result = new StringBuilder();

        // Execute the script resources one by one
        for (String scriptResourcePath : trigger.getScriptResourcePaths()) {

            ScriptResource.Type type = ScriptResource.path2type(scriptResourcePath);
            if (type == ScriptResource.Type.JS) {
                // JavaScript update
                evalJavaScriptResource(contextData, scriptResourcePath);
            } else if (type == FM) {
                // Freemarker Template update
                String html = applyFreemarkerTemplate(contextData, scriptResourcePath, language);
                result.append(html);
            }
        }

        return result.toString();
    }


    /**
     * Executes the Freemarker template at the given path and updates the message accordingly
     *
     * @param contextData        the context data to use in the Freemarker template
     * @param scriptResourcePath the path to the Freemarker template
     * @param language the language of the template execution
     */
    private String applyFreemarkerTemplate(
            Map<String, Object> contextData,
            String scriptResourcePath,
            String language) throws IOException, TemplateException {

        // Run the associated Freemarker template to get a result in the "FieldTemplates" format
        return templateService.newFmTemplateBuilder()
                .templatePath(scriptResourcePath)
                .data(contextData)
                .language(language)
                .dictionaryNames("message", "mail")
                .process();
    }


    /**
     * Evaluates the JavaScript resource at the given path
     *
     * @param contextData        the context data to use in the Freemarker template
     * @param scriptResourcePath the path to the Freemarker template
     */
    private void evalJavaScriptResource(Map<String, Object> contextData, String scriptResourcePath) throws Exception {

        javaScriptService.newJsResourceBuilder()
                .resourcePath(scriptResourcePath)
                .data(contextData)
                .evaluate();
    }
}