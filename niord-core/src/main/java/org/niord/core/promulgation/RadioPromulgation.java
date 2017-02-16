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

import org.niord.core.promulgation.vo.RadioPromulgationVo;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;

/**
 * Defines the promulgation data associated with NAVTEX mailing list promulgation
 */
@Entity
@DiscriminatorValue(RadioPromulgation.TYPE)
@SuppressWarnings("unused")
public class RadioPromulgation extends BasePromulgation<RadioPromulgationVo> implements IMailPromulgation {

    public static final String  TYPE = "radio";

    @Lob
    String text;

    /** Constructor **/
    public RadioPromulgation() {
        super();
        this.type = TYPE;
    }


    /** Constructor **/
    public RadioPromulgation(RadioPromulgationVo promulgation) {
        super(promulgation);
        this.type = TYPE;
        this.text = promulgation.getText();
    }


    /** Returns a value object for this entity */
    public RadioPromulgationVo toVo() {
        RadioPromulgationVo data = toVo(new RadioPromulgationVo());
        data.setText(text);
        return data;
    }


    /** Updates this promulgation from another promulgation **/
    @Override
    public void update(BasePromulgation promulgation) {
        if (promulgation instanceof RadioPromulgation) {
            super.update(promulgation);
            RadioPromulgation p = (RadioPromulgation)promulgation;
            p.setText(text);
        }
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    @Override
    public String getText() {
        return text;
    }

    @Override
    public void setText(String text) {
        this.text = text;
    }
}
