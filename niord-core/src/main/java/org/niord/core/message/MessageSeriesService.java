/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.message;

import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.sequence.DefaultSequence;
import org.niord.core.sequence.Sequence;
import org.niord.core.sequence.SequenceService;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business interface for managing message series
 */
@Stateless
public class MessageSeriesService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    SequenceService sequenceService;

    @Inject
    NiordApp app;

    /**
     * Returns the message series with the given series identifier
     * @param seriesId the series identifier
     * @return the message series with the given series identifier or null if not found
     */
    public MessageSeries findBySeriesId(String seriesId) {
        try {
            return em.createNamedQuery("MessageSeries.findBySeriesId", MessageSeries.class)
                    .setParameter("seriesId", seriesId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns all message series with the given series IDs
     * @param seriesIds the series IDs of the message series to look up
     * @return the list of all message series with the given series IDs
     */
    public List<MessageSeries> findByIds(String... seriesIds) {
        Set<String> serIds = new HashSet<>(Arrays.asList(seriesIds));
        return em.createNamedQuery("MessageSeries.findBySeriesIds", MessageSeries.class)
                .setParameter("seriesIds", serIds)
                .getResultList();
    }


    /**
     * Returns a list of persisted message series based on a list of template message series
     * @param series the list of message series to look up persisted series for
     * @return the list of corresponding persisted series
     */
    public List<MessageSeries> persistedMessageSeries(List<MessageSeries> series) {
        return series.stream()
                .map(ms -> findBySeriesId(ms.getSeriesId()))
                .filter(ms -> ms != null)
                .collect(Collectors.toList());
    }


    /**
     * Returns all message series
     * @return the list of all message series
     */
    public List<MessageSeries> getAllMessageSeries() {
        return getAll(MessageSeries.class);
    }


    /**
     * Searches for message series matching the given term
     *
     * @param term the search term
     * @param limit the maximum number of results
     * @return the search result
     */
    public List<MessageSeries> searchMessageSeries(String term, int limit) {

        if (StringUtils.isNotBlank(term)) {
            return em
                    .createNamedQuery("MessageSeries.searchMessageSeries", MessageSeries.class)
                    .setParameter("term", "%" + term + "%")
                    .setMaxResults(limit)
                    .getResultList();
        }
        return Collections.emptyList();
    }


    /**
     * Creates a new message series from the given template
     * @param series the new message series
     * @return the persisted message series
     */
    public MessageSeries createMessageSeries(MessageSeries series) {
        MessageSeries original = findBySeriesId(series.getSeriesId());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create message series with duplicate message series IDs"
                    + series.getSeriesId());
        }

        log.info("Creating new message series " + series.getSeriesId());
        return saveEntity(series);
    }


    /**
     * Updates an existing message series from the given template
     * @param series the message series to update
     * @return the persisted message series
     */
    public MessageSeries updateMessageSeries(MessageSeries series) {
        MessageSeries original = findBySeriesId(series.getSeriesId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update message series non-existing message series IDs"
                    + series.getSeriesId());
        }

        original.setMrnFormat(series.getMrnFormat());
        original.setMainType(series.getMainType());
        original.setShortFormat(series.getShortFormat());

        log.info("Updating message series " + series.getSeriesId());
        return saveEntity(original);
    }


    /**
     * Deletes the message series with the given Series ID
     * @param seriesId the message series to delete
     * @return if the message was deleted
     */
    public boolean deleteMessageSeries(String seriesId) {

        // TODO: Check if there are message with the given message series
        MessageSeries original = findBySeriesId(seriesId);
        if (original != null) {
            log.info("Removing message series " + seriesId);
            remove(original);
            return true;
        }
        return false;
    }

    /****************************/
    /** Message MRN Generation **/
    /****************************/

    /**
     * Creates a new message number specific for the given message series and year
     *
     * @param messageSeries the message series
     * @param year      the year
     * @return the new series identifier number
     */
    public Integer newMessageNumber(MessageSeries messageSeries, int year) {
        String sequenceKey = String.format("MESSAGE_SERIES_NUMBER_%s_%d", messageSeries.getSeriesId(), year);
        Sequence sequence = new DefaultSequence(sequenceKey, 1);
        return (int) sequenceService.getNextValue(sequence);
    }

    /**
     * Updates the message with a new message number, an MRN and a short ID
     * according to the associated message series
     *
     * @param message the message to update with a new message number, MRN and short ID
     */
    public void createNewIdentifiers(Message message) {
        MessageSeries messageSeries = message.getMessageSeries();
        Date publishDate = message.getPublishDate();

        if (messageSeries == null || publishDate == null) {
            throw new IllegalArgumentException("Message series and publish date must be specified");
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(publishDate);

        int year = cal.get(Calendar.YEAR);
        int week = cal.get(Calendar.WEEK_OF_YEAR);
        Integer number = newMessageNumber(messageSeries, year);

        Map<String, String> params = new HashMap<>();
        params.put("${year-2-digits}", String.valueOf(year).substring(2));
        params.put("${year}", String.valueOf(year));
        params.put("${week}", String.valueOf(week).substring(2));
        params.put("${week-2-digits}", String.format("%02d", week));
        params.put("${number}", String.valueOf(number));
        params.put("${number-3-digits}", String.format("%03d", number));
        params.put("${main-type}", message.getType().getMainType().toString());
        params.put("${main-type-lower}", message.getType().getMainType().toString().toLowerCase());
        params.put("${country}", app.getCountry());

        message.setNumber(number);
        message.setMrn(messageSeries.getMrnFormat());
        message.setShortId(messageSeries.getShortFormat());
        params.entrySet().forEach(e -> {
            message.setMrn(message.getMrn().replace(e.getKey(), e.getValue()));
            if (StringUtils.isNotBlank(message.getShortId())) {
                message.setShortId(message.getShortId().replace(e.getKey(), e.getValue()));
            }
        });

    }

}