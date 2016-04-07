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

import org.niord.core.service.BaseService;
import org.niord.core.user.User;
import org.niord.core.user.UserService;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Business interface for managing message filters
 */
@Stateless
@SuppressWarnings("unused")
public class MessageFilterService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    UserService userService;


    /**
     * Returns the message filters with the given identifier
     * @param id the message filter
     * @return the message filters with the given identifier or null if not found
     */
    public MessageFilter findById(Integer id) {
        User user = userService.currentUser();

        // Look up the given filter
        MessageFilter filter = getByPrimaryKey(MessageFilter.class, id);

        // Check that the current user is the owner of the filter
        if (filter != null && !user.getId().equals(filter.getUser().getId())) {
            throw new IllegalArgumentException("User " + user.getUsername() + " does now own filter " + id);
        }
        return filter;
    }


    /**
     * Returns the message filter for the current user with the given name
     * @param name the name of the message filter to look up
     * @return the matching message filter or null
     */
    public MessageFilter findByCurrentUserAndName(String name) {
        User user = userService.currentUser();

        try {
            return em.createNamedQuery("MessageFilter.findByUserAndName", MessageFilter.class)
                    .setParameter("user", user)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns all message filters for the current user with the given IDs
     * @param ids the message filter IDs of the message filters to look up
     * @return the list of all message filters for the current user with the given IDs
     */
    public List<MessageFilter> findByCurrentUserAndIds(Integer... ids) {
        User user = userService.currentUser();

        Set<Integer> filterIds = new HashSet<>(Arrays.asList(ids));
        return em.createNamedQuery("MessageFilter.findByUserAndIds", MessageFilter.class)
                .setParameter("user", user)
                .setParameter("ids", filterIds)
                .getResultList();
    }


    /**
     * Returns all message filters for the current user
     * @return the list of all message filters for the current user
     */
    public List<MessageFilter> getMessageFiltersForUser() {
        User user = userService.currentUser();
        return em.createNamedQuery("MessageFilter.findByUser", MessageFilter.class)
                .setParameter("user", user)
                .getResultList();
    }


    /**
     * Creates a new message filter for the current user from the given template.
     * If a filter with the given name already exists, update this filter instead.
     * @param filter the new message filter
     * @return the persisted message filter
     */
    public MessageFilter createOrUpdateMessageFilter(MessageFilter filter) {
        MessageFilter original;

        // Search for an existing filter with the same ID or name
        if (filter.getId() != null) {
            original = findById(filter.getId());
            if (original == null) {
                throw new IllegalArgumentException("Invalid filter id  " + filter.getId());
            }
        } else {
            original = findByCurrentUserAndName(filter.getName());
        }

        // Update an existing filter
        if (original != null) {
            original.setName(filter.getName());
            original.setParameters(filter.getParameters());
            return saveEntity(original);
        }

        // Create a new filter
        filter.setUser(userService.currentUser());
        return saveEntity(filter);
    }


    /**
     * Deletes the message filter for the current user with the given ID
     * @param id the message filter to delete
     * @return if the message filter was deleted
     */
    public boolean deleteMessageFilter(Integer id) {
        User user = userService.currentUser();

        MessageFilter original = findById(id);
        if (original != null) {
            log.info("Removing message filter " + id);
            remove(original);
            return true;
        }
        return false;
    }

}