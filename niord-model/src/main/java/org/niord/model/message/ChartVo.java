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
package org.niord.model.message;

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;
import org.niord.model.geojson.GeometryVo;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Represents a chart
 */
@ApiModel(value = "Chart", description = "Sea chart model")
@XmlRootElement(name = "chart")
@XmlType(propOrder = {
        "chartNumber", "internationalNumber", "active", "geometry", "horizontalDatum", "scale", "name"
})
public class ChartVo implements IJsonSerializable {

    String chartNumber;
    Integer internationalNumber;
    boolean active = true;
    GeometryVo geometry;
    String horizontalDatum;
    Integer scale;
    String name;

    /**
     * Returns a string representation of the chart including chart number and international number
     * @return a string representation of the chart
     */
    @SuppressWarnings("unused")
    public String getFullChartNumber() {
        return (internationalNumber == null)
                ? chartNumber
                : String.format("%s (INT %d)", chartNumber, internationalNumber);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getChartNumber() {
        return chartNumber;
    }

    public void setChartNumber(String chartNumber) {
        this.chartNumber = chartNumber;
    }

    public Integer getInternationalNumber() {
        return internationalNumber;
    }

    public void setInternationalNumber(Integer internationalNumber) {
        this.internationalNumber = internationalNumber;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public GeometryVo getGeometry() {
        return geometry;
    }

    public void setGeometry(GeometryVo geometry) {
        this.geometry = geometry;
    }

    public String getHorizontalDatum() {
        return horizontalDatum;
    }

    public void setHorizontalDatum(String horizontalDatum) {
        this.horizontalDatum = horizontalDatum;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}