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

import org.niord.core.model.BaseEntity;
import org.niord.core.user.User;
import org.niord.model.vo.MessageTagVo;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Tags represents a named collection of messages.
 * They may be shared, or tied to a specific user.
 * Also, they may have an expiry date, after which they are purged.
 */
@Entity
@Table(
    uniqueConstraints = @UniqueConstraint(columnNames = { "tagId", "user_id" })
)
@NamedQueries({
        @NamedQuery(name="MessageTag.findByUser",
                query="SELECT t FROM MessageTag t where t.user is null or t.user = :user"),
        @NamedQuery(name="MessageTag.findShared",
                query="SELECT t FROM MessageTag t where t.user is null"),
        @NamedQuery(name="MessageTag.findByUserAndTagIds",
                query="SELECT t FROM MessageTag t where t.tagId in (:tagIds) and (t.user is null or t.user = :user)"),
        @NamedQuery(name="MessageTag.findSharedByTagIds",
                query="SELECT t FROM MessageTag t where t.tagId in (:tagIds) and t.user is null"),
        @NamedQuery(name  = "MessageTag.searchSharedMessageTags",
                query="SELECT t FROM MessageTag t where lower(t.tagId) like lower(:term) "
                        + "and t.user is null"),
        @NamedQuery(name  = "MessageTag.searchMessageTagsByUser",
                query="SELECT t FROM MessageTag t where lower(t.tagId) like lower(:term) "
                        + "and (t.user is null or t.user = :user)"),
        @NamedQuery(name= "MessageTag.findExpiredMessageTags",
                query="SELECT t FROM MessageTag t where t.expiryDate is not null and t.expiryDate < current_timestamp"),
})
@SuppressWarnings("unused")
public class MessageTag extends BaseEntity<Integer> {

    @NotNull
    String tagId;

    @ManyToOne
    User user;

    @Temporal(TemporalType.TIMESTAMP)
    Date expiryDate;

    @ManyToMany
    List<Message> messages = new ArrayList<>();

    // More efficient than counting related messages
    @Column(columnDefinition="INT default 0")
    int messageCount;

    /**
     * Constructor
     */
    public MessageTag() {
    }

    /**
     * Constructor
     */
    public MessageTag(MessageTagVo tag, User user) {
        this.tagId = tag.getTagId();
        this.expiryDate = tag.getExpiryDate();
        this.user = tag.isShared() ? null : user;
    }


    /** Converts this entity to a value object */
    public MessageTagVo toVo() {
        MessageTagVo tag = new MessageTagVo();
        tag.setTagId(tagId);
        tag.setExpiryDate(expiryDate);
        tag.setShared(user == null);
        tag.setMessageCount(messageCount);
        return tag;
    }


    /** Update the number of messages */
    @PrePersist
    public void updateMessageCount() {
        messageCount = messages.size();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getTagId() {
        return tagId;
    }

    public void setTagId(String tagId) {
        this.tagId = tagId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }
}