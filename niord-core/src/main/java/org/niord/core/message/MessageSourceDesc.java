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

package org.niord.core.message;

import org.niord.core.message.vo.MessageSourceDescVo;
import org.niord.core.model.DescEntity;
import org.niord.model.ILocalizedDesc;

import javax.persistence.Entity;

/**
 * Localized contents for the MessageSource entity
 */
@Entity
@SuppressWarnings("unused")
public class MessageSourceDesc extends DescEntity<MessageSource> {

    String name;

    /** Constructor */
    public MessageSourceDesc() {
    }


    /** Constructor */
    public MessageSourceDesc(MessageSourceDescVo desc) {
        super(desc);
        this.name = desc.getName();
    }


    /** Converts this entity to a value object */
    public MessageSourceDescVo toVo() {
        MessageSourceDescVo desc = new MessageSourceDescVo();
        desc.setLang(lang);
        desc.setName(name);
        return desc;
    }


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(name);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        MessageSourceDesc desc = (MessageSourceDesc)localizedDesc;
        this.lang = desc.getLang();
        this.name = desc.getName();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

