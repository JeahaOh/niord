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

package org.niord.core.promulgation;

import org.niord.core.message.Message;

import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;

/**
 * Manages NAVTEX-via-mailing-lists promulgations
 */
@Singleton
@Startup
@Lock(LockType.READ)
@SuppressWarnings("unused")
public class NavtexPromulgationService extends BasePromulgationService {

    public static final int PRIORITY = 1;

    /**
     * Registers the promulgation service with the promulgation manager
     */
    @PostConstruct
    public void init() {
        registerPromulgationService();
    }


    /** {@inheritDoc} */
    @Override
    public String getType() {
        return NavtexPromulgation.TYPE;
    }


    /** {@inheritDoc} */
    @Override
    public int getDefaultPriority() {
        return PRIORITY;
    }


    /** {@inheritDoc} */
    @Override
    public void onNewTemplateMessage(Message message) {
        NavtexPromulgation navtex = new NavtexPromulgation();
        navtex.getTransmitters().put("Baltico", Boolean.FALSE);
        navtex.getTransmitters().put("Rogaland", Boolean.FALSE);
        message.getPromulgations().add(navtex);
    }


    /** {@inheritDoc} */
    @Override
    public void onCreateMessage(Message message) {
    }


    /** {@inheritDoc} */
    @Override
    public void onUpdateMessage(Message message) {
    }


    /** {@inheritDoc} */
    @Override
    public void onUpdateMessageStatus(Message message) {
    }
}
