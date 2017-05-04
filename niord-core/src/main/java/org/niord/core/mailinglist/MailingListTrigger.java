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

import org.niord.core.mailinglist.vo.MailingListTriggerDescVo;
import org.niord.core.mailinglist.vo.MailingListTriggerVo;
import org.niord.core.model.VersionedEntity;
import org.niord.core.script.ScriptResource;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.message.Status;

import javax.persistence.CascadeType;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.core.mailinglist.TriggerType.SCHEDULED;
import static org.niord.core.mailinglist.TriggerType.STATUS_CHANGE;

/**
 * Defines a mailing list trigger.
 * <p>
 * A mailing list trigger is either scheduled or status change driven, and will cause
 * the mailing list to be enacted.
 */
@Entity
@NamedQueries({
        @NamedQuery(name = "MailingListTrigger.findPendingScheduledTriggers",
                query = "SELECT t FROM MailingListTrigger t where t.type = 'SCHEDULED' "
                        + " and t.mailingList.active = true and t.nextScheduledExecution < current_timestamp"),
        @NamedQuery(name = "MailingListTrigger.findStatusChangeTriggers",
                query = "SELECT t FROM MailingListTrigger t join t.statusChanges s where t.type = 'STATUS_CHANGE' "
                        + " and t.mailingList.active = true and s = :status")
})
@SuppressWarnings("unused")
public class MailingListTrigger extends VersionedEntity<Integer> implements ILocalizable<MailingListTriggerDesc> {

    @NotNull
    @ManyToOne
    MailingList mailingList;

    @NotNull
    @Enumerated(EnumType.STRING)
    TriggerType type;

    @Enumerated(EnumType.STRING)
    ScheduleType scheduleType;

    /**
     * We actually only use the time of day for the persisted date, but avoid time zone issues by storing
     * the entire date, rather than just the time part, using TemporalType.TIME
     **/
    @Temporal(TemporalType.TIMESTAMP)
    Date scheduledTimeOfDay;

    @Temporal(TemporalType.TIMESTAMP)
    Date nextScheduledExecution;

    @ElementCollection(targetClass = Status.class)
    @Enumerated(EnumType.STRING)
    Set<Status> statusChanges = new HashSet<>();

    /**
     * Defines a message query string used for fetching the messages of a scheduled trigger
     * <p>
     * Example filter:
     * <pre>
     *   "messageSeries=dma-nw&status=PUBLISHED&type=COASTAL_WARNING"
     * </pre>
     */
    String messageQuery;

    /**
     * Define a server-side JavaScript that filters messages in a status-change trigger.
     * <p>
     * Example filter:
     * <pre>
     *   "msg.promulgation('navtex').promulgate && msg.promulgation('navtex').useTransmitter('Baltico')"
     * </pre>
     */
    String messageFilter;

    /** The script resources to execute to generate the mail body **/
    @OrderColumn(name = "indexNo")
    @ElementCollection
    List<String> scriptResourcePaths = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<MailingListTriggerDesc> descs = new ArrayList<>();


    /** Constructor **/
    public MailingListTrigger() {
    }


    /** Constructor **/
    public MailingListTrigger(MailingListTriggerVo trigger) {

        this.type = trigger.getType();
        this.scheduleType = trigger.getScheduleType();
        this.scheduledTimeOfDay = trigger.getScheduledTimeOfDay();
        if (trigger.getStatusChanges() != null) {
            statusChanges = new HashSet<>(trigger.getStatusChanges());
        }
        this.messageQuery = trigger.getMessageQuery();
        this.messageFilter = trigger.getMessageFilter();
        if (trigger.getScriptResourcePaths() != null) {
            trigger.getScriptResourcePaths().stream()
                    .filter(p -> ScriptResource.path2type(p) != null)
                    .forEach(p -> this.scriptResourcePaths.add(p));
        }
        if (trigger.getDescs() != null) {
            trigger.getDescs().stream()
                    .filter(MailingListTriggerDescVo::descDefined)
                    .forEach(desc -> createDesc(desc.getLang())
                            .copyDesc(new MailingListTriggerDesc(desc)));
        }

        // Reset fields that are not valid for the current trigger type
        if (type == STATUS_CHANGE) {
            scheduleType = null;
            scheduledTimeOfDay = null;
            messageQuery = null;
        } else if (type == SCHEDULED) {
            statusChanges.clear();
            messageFilter = null;
        }
    }


    /** Converts this entity to a value object */
    public MailingListTriggerVo toVo(DataFilter filter) {
        DataFilter compFilter = filter.forComponent(MailingListTrigger.class);

        MailingListTriggerVo trigger = new MailingListTriggerVo();
        trigger.setType(type);
        trigger.setScheduleType(scheduleType);
        trigger.setScheduledTimeOfDay(scheduledTimeOfDay);
        if (!statusChanges.isEmpty()) {
            trigger.setStatusChanges(new HashSet<>(statusChanges));
        }
        trigger.setMessageQuery(messageQuery);
        trigger.setMessageFilter(messageFilter);
        if (!scriptResourcePaths.isEmpty()) {
            trigger.setScriptResourcePaths(new ArrayList<>(scriptResourcePaths));
        }
        trigger.setDescs(getDescs(filter).stream()
                .map(MailingListTriggerDesc::toVo)
                .collect(Collectors.toList()));

        return trigger;
    }


    /**
     * If the trigger is a scheduled trigger,
     * computes the next scheduled execution
     */
    public void checkComputeNextScheduledExecution() {
        if (type == SCHEDULED && scheduleType != null && scheduledTimeOfDay != null) {
            Calendar timeOfDay = Calendar.getInstance();
            timeOfDay.setTime(scheduledTimeOfDay);

            Calendar nextExecution = Calendar.getInstance();
            nextExecution.set(Calendar.HOUR_OF_DAY, timeOfDay.get(Calendar.HOUR_OF_DAY));
            nextExecution.set(Calendar.MINUTE,      timeOfDay.get(Calendar.MINUTE));
            nextExecution.set(Calendar.SECOND,      timeOfDay.get(Calendar.SECOND));
            nextExecution.set(Calendar.MILLISECOND, 0);

            Date now = new Date();
            if (scheduleType == ScheduleType.DAILY) {
                if (nextExecution.getTime().before(now)) {
                    nextExecution.add(Calendar.DAY_OF_WEEK, 1);
                }
            } else {
                nextExecution.set(Calendar.DAY_OF_WEEK, scheduleType.getCalendarField());
                if (nextExecution.getTime().before(now)) {
                    nextExecution.add(Calendar.WEEK_OF_YEAR, 1);
                }
            }
            this.nextScheduledExecution = nextExecution.getTime();
        }
    }


    /** {@inheritDoc} */
    @Override
    public MailingListTriggerDesc createDesc(String lang) {
        MailingListTriggerDesc desc = new MailingListTriggerDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public MailingList getMailingList() {
        return mailingList;
    }

    public void setMailingList(MailingList mailingList) {
        this.mailingList = mailingList;
    }

    public TriggerType getType() {
        return type;
    }

    public void setType(TriggerType type) {
        this.type = type;
    }

    public ScheduleType getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(ScheduleType scheduleType) {
        this.scheduleType = scheduleType;
    }

    public Date getScheduledTimeOfDay() {
        return scheduledTimeOfDay;
    }

    public void setScheduledTimeOfDay(Date scheduledTimeOfDay) {
        this.scheduledTimeOfDay = scheduledTimeOfDay;
    }

    public Set<Status> getStatusChanges() {
        return statusChanges;
    }

    public void setStatusChanges(Set<Status> statusChanges) {
        this.statusChanges = statusChanges;
    }

    public String getMessageQuery() {
        return messageQuery;
    }

    public void setMessageQuery(String messageQuery) {
        this.messageQuery = messageQuery;
    }

    public String getMessageFilter() {
        return messageFilter;
    }

    public void setMessageFilter(String messageFilter) {
        this.messageFilter = messageFilter;
    }

    public List<String> getScriptResourcePaths() {
        return scriptResourcePaths;
    }

    public void setScriptResourcePaths(List<String> scriptResourcePaths) {
        this.scriptResourcePaths = scriptResourcePaths;
    }

    @Override
    public List<MailingListTriggerDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MailingListTriggerDesc> descs) {
        this.descs = descs;
    }

}